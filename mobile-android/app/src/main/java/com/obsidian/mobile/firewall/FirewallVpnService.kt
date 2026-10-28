package com.obsidian.mobile.firewall

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.obsidian.mobile.AppGraph
import com.obsidian.mobile.MainActivity
import com.obsidian.mobile.ObsidianApplication
import com.obsidian.mobile.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * The phone firewall: a local VPN that carries only DNS (and traffic to
 * blocked IPv4 addresses), so the rest of the phone's traffic never passes
 * through the app. A blocked name gets "no such domain"; an allowed lookup is
 * forwarded to the network's own DNS server and answered as usual. Packets to
 * a blocked address are dropped. Android lets an app see neither other apps'
 * incoming connections nor DNS sent over Private DNS (DNS-over-TLS) or a
 * browser's secure DNS; those bypass this filter.
 */
class FirewallVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val forwarders = Executors.newFixedThreadPool(4)
    private var tun: ParcelFileDescriptor? = null
    private var loop: Job? = null
    @Volatile private var policy: FirewallPolicy = FirewallPolicy.EMPTY
    @Volatile private var upstream: InetAddress = InetAddress.getByName(FALLBACK_DNS)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android restarts a sticky service on its own after the process dies: only come back when the
        // user still wants the firewall and is signed in (a sign-out clears the wish and the rules).
        val session = AppGraph.session
        if (intent?.action == ACTION_STOP || !session.settings.value.firewallWanted || !session.isSignedIn) {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
                if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
            stopFirewall()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0)
        if (loop == null) {
            policy = AppGraph.protection.policy.value
            establish()
            scope.launch {
                // Rebuild the tunnel when the rules change: blocked IPs are routes, and a new
                // VPN network starts with an empty DNS cache, so a newly blocked name stops resolving at once.
                AppGraph.protection.policy.drop(1).collect {
                    val changed = it.signature != policy.signature
                    policy = it
                    if (changed) establish(freshNetwork = true)
                }
            }
            scope.launch {
                while (isActive) {
                    runCatching { AppGraph.protection.syncBlocklist() }
                    delay(SYNC_EVERY_MS)
                }
            }
        }
        _running.value = true
        return START_STICKY
    }

    override fun onRevoke() {
        // Another VPN took over, or the user turned us off in system settings.
        AppGraph.session.setFirewallWanted(false)
        stopFirewall()
    }

    override fun onDestroy() {
        stopFirewall()
        forwarders.shutdownNow()
        scope.cancel()
        super.onDestroy()
    }

    /**
     * (Re)builds the tunnel. Android keeps a VPN network, and the DNS answers it has cached, across
     * rebuilds. After a rule change ([freshNetwork]) the old tunnel is closed first and Android is
     * given a moment to drop it, so the new one is a new network with an empty cache and a newly
     * blocked name stops resolving at once instead of after its TTL. Lookups made during that
     * moment (well under a second) aren't filtered.
     */
    private fun establish(freshNetwork: Boolean = false) {
        upstream = systemDns() ?: InetAddress.getByName(FALLBACK_DNS)
        if (freshNetwork && tun != null) {
            loop?.cancel()
            runCatching { tun?.close() }
            tun = null
            awaitVpnGone()
        }
        val builder = Builder()
            .setSession("Obsidian firewall")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .setBlocking(true)
        policy.blockedIpv4.take(MAX_IP_ROUTES).forEach { runCatching { builder.addRoute(it, 32) } }
        runCatching { builder.addDisallowedApplication(packageName) } // our own lookups and API calls go direct
        val next = builder.establish() ?: run { stopFirewall(); return }
        val previous = tun
        tun = next
        loop?.cancel()
        loop = scope.launch { pump(next) }
        previous?.close()
    }

    private fun pump(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        while (scope.isActive) {
            val length = try {
                input.read(buffer)
            } catch (e: IOException) {
                return // tunnel closed
            }
            if (length <= 0) continue
            val packet = Packets.parseIpv4(buffer, length) ?: continue
            if (packet.destinationAddress == DNS_ADDRESS) {
                // Plain DNS is answered; anything else (Android probing for DNS-over-TLS on port 853)
                // is dropped quietly, and the phone falls back to plain DNS.
                if (packet.protocol == Packets.PROTOCOL_UDP && packet.destinationPort == 53) {
                    handleDns(buffer.copyOf(length), packet, output)
                }
            } else {
                // Only blocked addresses are routed here: drop and log.
                val address = packet.destinationAddress
                val decision = policy.decideIp(address)
                FirewallLog.add(FirewallLog.Direction.OUTGOING, address, appName(packet), true,
                    decision.reason.takeIf { decision.blocked } ?: FirewallPolicy.Reason.SHARED_BLOCK,
                    if (packet.destinationPort > 0) "port ${packet.destinationPort}" else null)
            }
        }
    }

    private fun handleDns(raw: ByteArray, packet: Packets.Ipv4, output: FileOutputStream) {
        val question = Dns.question(raw, packet.payloadOffset, packet.payloadLength) ?: return
        val decision = policy.decideDomain(question.name)
        val app = appName(packet)
        FirewallLog.add(FirewallLog.Direction.OUTGOING, question.name, app, decision.blocked, decision.reason,
            typeName(question.type))
        if (decision.blocked) {
            write(output, Packets.udpReply(packet, Dns.nxdomain(raw, packet.payloadOffset, question)))
            FirewallLog.add(FirewallLog.Direction.INCOMING, question.name, app, true, decision.reason, "No such domain (blocked)")
            return
        }
        val query = raw.copyOfRange(packet.payloadOffset, packet.payloadOffset + packet.payloadLength)
        forwarders.execute {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 5_000
                    socket.send(DatagramPacket(query, query.size, InetSocketAddress(upstream, 53)))
                    val reply = DatagramPacket(ByteArray(4096), 4096)
                    socket.receive(reply)
                    val answer = reply.data.copyOf(reply.length)
                    write(output, Packets.udpReply(packet, answer))
                    val addresses = Dns.answers(answer)
                    if (question.type == Dns.TYPE_A || question.type == Dns.TYPE_AAAA) {
                        FirewallLog.add(FirewallLog.Direction.INCOMING, question.name, app, false, decision.reason,
                            addresses.joinToString(", ").ifEmpty { "No address" })
                    }
                }
            } catch (e: IOException) {
                // Upstream timeout: the phone's resolver retries on its own.
            }
        }
    }

    private fun write(output: FileOutputStream, packet: ByteArray) {
        synchronized(output) {
            runCatching { output.write(packet) }
        }
    }

    private fun appName(packet: Packets.Ipv4): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val protocol = if (packet.protocol == Packets.PROTOCOL_TCP) OsConstants.IPPROTO_TCP else OsConstants.IPPROTO_UDP
        val uid = runCatching {
            getSystemService(ConnectivityManager::class.java).getConnectionOwnerUid(protocol,
                InetSocketAddress(InetAddress.getByAddress(packet.source), packet.sourcePort),
                InetSocketAddress(InetAddress.getByAddress(packet.destination), packet.destinationPort))
        }.getOrNull() ?: return null
        if (uid < 0) return null
        // Android may report an owner in a managed profile that this app cannot inspect.
        val pkg = try {
            packageManager.getPackagesForUid(uid)?.firstOrNull()
        } catch (_: SecurityException) {
            return null
        } ?: return null
        return runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
    }

    /** The current network's own DNS server (we're excluded from our VPN, so this is the real one). */
    private fun systemDns(): InetAddress? {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val network = connectivity.activeNetwork ?: return null
        val servers = connectivity.getLinkProperties(network)?.dnsServers ?: return null
        return servers.firstOrNull { it is Inet4Address && it.hostAddress != DNS_ADDRESS }
    }

    /** Waits (up to 2 s) until Android no longer lists a VPN network, after our tunnel was closed. */
    private fun awaitVpnGone() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val deadline = System.currentTimeMillis() + 2_000
        while (System.currentTimeMillis() < deadline) {
            @Suppress("DEPRECATION")
            val vpnUp = connectivity.allNetworks.any {
                connectivity.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            }
            if (!vpnUp) return
            Thread.sleep(50)
        }
    }

    private fun stopFirewall() {
        loop?.cancel()
        loop = null
        runCatching { tun?.close() }
        tun = null
        _running.value = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, "FIREWALL"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, ObsidianApplication.CHANNEL_FIREWALL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Firewall is on")
            .setContentText("Blocking known scam and malware sites")
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    private fun typeName(type: Int) = when (type) {
        Dns.TYPE_A -> "IPv4 lookup"
        Dns.TYPE_AAAA -> "IPv6 lookup"
        65 -> "HTTPS record lookup"
        else -> "DNS type $type"
    }

    companion object {
        const val TUN_ADDRESS = "10.111.222.1"
        const val DNS_ADDRESS = "10.111.222.2"
        private const val FALLBACK_DNS = "1.1.1.1"
        private const val MAX_IP_ROUTES = 256
        private const val SYNC_EVERY_MS = 30 * 60 * 1000L
        private const val NOTIFICATION_ID = 7001
        private const val ACTION_STOP = "com.obsidian.mobile.firewall.STOP"

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        /** Null when the user has already allowed the VPN; otherwise the system consent screen to launch first. */
        fun consentIntent(context: Context): Intent? = prepare(context)

        fun start(context: Context) {
            context.startForegroundService(Intent(context, FirewallVpnService::class.java))
        }

        fun stop(context: Context) {
            if (_running.value) {
                context.startService(Intent(context, FirewallVpnService::class.java).setAction(ACTION_STOP))
            }
        }
    }
}
