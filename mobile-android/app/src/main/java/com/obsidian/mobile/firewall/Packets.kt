package com.obsidian.mobile.firewall

import java.net.InetAddress

/**
 * Just enough IPv4, UDP and DNS for a DNS-only VPN: read a query out of a
 * packet the phone sent to our DNS address, and wrap an answer back up as the
 * packet the phone expects. No IP options are written; UDP checksums are left
 * at 0, which IPv4 allows.
 */
object Packets {

    const val PROTOCOL_TCP = 6
    const val PROTOCOL_UDP = 17
    private const val IPV4_HEADER = 20
    private const val UDP_HEADER = 8

    /** One IPv4 packet's addresses and, for TCP/UDP, ports and payload offset. */
    data class Ipv4(
        val protocol: Int,
        val source: ByteArray,
        val destination: ByteArray,
        val sourcePort: Int,
        val destinationPort: Int,
        val payloadOffset: Int,
        val payloadLength: Int,
    ) {
        val destinationAddress: String get() = InetAddress.getByAddress(destination).hostAddress ?: ""
    }

    fun parseIpv4(packet: ByteArray, length: Int): Ipv4? {
        if (length < IPV4_HEADER || (packet[0].toInt() ushr 4) != 4) return null
        val headerLength = (packet[0].toInt() and 0x0f) * 4
        val totalLength = u16(packet, 2).coerceAtMost(length)
        if (headerLength < IPV4_HEADER || totalLength < headerLength) return null
        val protocol = packet[9].toInt() and 0xff
        val source = packet.copyOfRange(12, 16)
        val destination = packet.copyOfRange(16, 20)
        val hasPorts = (protocol == PROTOCOL_UDP || protocol == PROTOCOL_TCP) && totalLength >= headerLength + 4
        val payloadOffset = headerLength + if (protocol == PROTOCOL_UDP) UDP_HEADER else 0
        return Ipv4(
            protocol = protocol,
            source = source,
            destination = destination,
            sourcePort = if (hasPorts) u16(packet, headerLength) else 0,
            destinationPort = if (hasPorts) u16(packet, headerLength + 2) else 0,
            payloadOffset = payloadOffset,
            payloadLength = (totalLength - payloadOffset).coerceAtLeast(0),
        )
    }

    /** The UDP reply to [query]: addresses and ports swapped, carrying [payload]. */
    fun udpReply(query: Ipv4, payload: ByteArray): ByteArray {
        val total = IPV4_HEADER + UDP_HEADER + payload.size
        val packet = ByteArray(total)
        packet[0] = 0x45
        put16(packet, 2, total)
        packet[6] = 0x40 // don't fragment
        packet[8] = 64 // TTL
        packet[9] = PROTOCOL_UDP.toByte()
        query.destination.copyInto(packet, 12)
        query.source.copyInto(packet, 16)
        put16(packet, 10, checksum(packet, 0, IPV4_HEADER))
        put16(packet, IPV4_HEADER, query.destinationPort)
        put16(packet, IPV4_HEADER + 2, query.sourcePort)
        put16(packet, IPV4_HEADER + 4, UDP_HEADER + payload.size)
        payload.copyInto(packet, IPV4_HEADER + UDP_HEADER)
        return packet
    }

    internal fun checksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i < offset + length - 1) {
            sum += u16(data, i)
            i += 2
        }
        if (length % 2 == 1) sum += (data[offset + length - 1].toInt() and 0xff) shl 8
        while (sum shr 16 != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }

    internal fun u16(data: ByteArray, offset: Int) =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    internal fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }
}

/** A DNS message's first question, and the IPv4/IPv6 answers of a response. */
object Dns {

    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    private const val HEADER = 12

    data class Question(val name: String, val type: Int, /** Header + question bytes, for building a reply. */ val endOffset: Int)

    fun question(message: ByteArray, offset: Int = 0, length: Int = message.size - offset): Question? {
        if (length < HEADER + 5) return null
        if (Packets.u16(message, offset + 4) < 1) return null
        val labels = mutableListOf<String>()
        var i = offset + HEADER
        val end = offset + length
        while (true) {
            if (i >= end) return null
            val size = message[i].toInt() and 0xff
            if (size == 0) { i++; break }
            if (size and 0xc0 != 0 || i + 1 + size > end) return null // no compression in a question we'd accept
            labels += String(message, i + 1, size, Charsets.US_ASCII)
            i += 1 + size
        }
        if (i + 4 > end) return null
        val type = Packets.u16(message, i)
        return Question(labels.joinToString(".").lowercase(), type, i + 4 - offset)
    }

    /**
     * "This name doesn't exist" for [query]: same ID and question, QR and RA set,
     * RD copied, RCODE 3. Anything after the question (e.g. EDNS) is dropped.
     */
    fun nxdomain(query: ByteArray, offset: Int, question: Question): ByteArray {
        val reply = query.copyOfRange(offset, offset + question.endOffset)
        val flags = Packets.u16(reply, 2)
        Packets.put16(reply, 2, 0x8000 or (flags and 0x7900) or 0x0080 or 3)
        Packets.put16(reply, 4, 1)
        Packets.put16(reply, 6, 0)
        Packets.put16(reply, 8, 0)
        Packets.put16(reply, 10, 0)
        return reply
    }

    /** Addresses in a response's answer section (A and AAAA records), for the request log. */
    fun answers(response: ByteArray): List<String> {
        if (response.size < HEADER) return emptyList()
        val questions = Packets.u16(response, 4)
        val answers = Packets.u16(response, 6)
        var i = HEADER
        repeat(questions) {
            i = skipName(response, i) ?: return emptyList()
            i += 4
        }
        val addresses = mutableListOf<String>()
        repeat(answers) {
            i = skipName(response, i) ?: return addresses
            if (i + 10 > response.size) return addresses
            val type = Packets.u16(response, i)
            val dataLength = Packets.u16(response, i + 8)
            val data = i + 10
            if (data + dataLength > response.size) return addresses
            if ((type == TYPE_A && dataLength == 4) || (type == TYPE_AAAA && dataLength == 16)) {
                InetAddress.getByAddress(response.copyOfRange(data, data + dataLength)).hostAddress?.let(addresses::add)
            }
            i = data + dataLength
        }
        return addresses
    }

    private fun skipName(message: ByteArray, start: Int): Int? {
        var i = start
        while (i < message.size) {
            val size = message[i].toInt() and 0xff
            when {
                size == 0 -> return i + 1
                size and 0xc0 == 0xc0 -> return i + 2
                else -> i += 1 + size
            }
        }
        return null
    }
}
