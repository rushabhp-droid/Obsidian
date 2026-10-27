package com.obsidian.mobile.firewall

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PacketsTest {

    /** A DNS query for [name] (type A, RD set, with an EDNS OPT record after the question). */
    private fun dnsQuery(name: String, id: Int = 0x1234): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(byteArrayOf((id shr 8).toByte(), id.toByte(), 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 1))
        name.split('.').forEach { label ->
            out.write(label.length)
            out.write(label.toByteArray())
        }
        out.write(byteArrayOf(0, 0, 1, 0, 1))
        out.write(byteArrayOf(0, 0, 41, 0x10, 0, 0, 0, 0, 0, 0, 0)) // OPT
        return out.toByteArray()
    }

    /** An IPv4/UDP packet from 10.111.222.1:40000 to the firewall's DNS address. */
    private fun udpPacket(payload: ByteArray): ByteArray {
        val query = Packets.Ipv4(Packets.PROTOCOL_UDP, byteArrayOf(10, 111, 222.toByte(), 2), byteArrayOf(10, 111, 222.toByte(), 1),
            53, 40000, 28, payload.size)
        return Packets.udpReply(query, payload) // a reply of the reply: phone → DNS server
    }

    @Test
    fun parsesAQueryPacket() {
        val raw = udpPacket(dnsQuery("Login.Scam.Example"))
        val ip = Packets.parseIpv4(raw, raw.size)!!
        assertEquals(Packets.PROTOCOL_UDP, ip.protocol)
        assertEquals("10.111.222.2", ip.destinationAddress)
        assertEquals(53, ip.destinationPort)
        assertEquals(40000, ip.sourcePort)
        val question = Dns.question(raw, ip.payloadOffset, ip.payloadLength)!!
        assertEquals("login.scam.example", question.name)
        assertEquals(Dns.TYPE_A, question.type)
    }

    @Test
    fun nxdomainKeepsIdAndQuestionAndDropsEdns() {
        val query = dnsQuery("scam.example", id = 0xBEEF)
        val question = Dns.question(query)!!
        val reply = Dns.nxdomain(query, 0, question)
        assertEquals(question.endOffset, reply.size)
        assertEquals(0xBEEF, Packets.u16(reply, 0))
        assertEquals(0x8183, Packets.u16(reply, 2)) // QR, RD, RA, NXDOMAIN
        assertEquals(1, Packets.u16(reply, 4))
        assertEquals(0, Packets.u16(reply, 10))
        assertArrayEquals(query.copyOfRange(12, question.endOffset), reply.copyOfRange(12, reply.size))
    }

    @Test
    fun replyHeaderChecksumIsValid() {
        val raw = udpPacket(dnsQuery("example.com"))
        val ip = Packets.parseIpv4(raw, raw.size)!!
        val reply = Packets.udpReply(ip, byteArrayOf(1, 2, 3))
        assertEquals(0, Packets.checksum(reply, 0, 20)) // a correct header sums to zero
        assertEquals(53, Packets.u16(reply, 20))
        assertEquals(40000, Packets.u16(reply, 22))
        assertEquals(11, Packets.u16(reply, 24))
        assertEquals("10.111.222.1", java.net.InetAddress.getByAddress(reply.copyOfRange(16, 20)).hostAddress)
    }

    @Test
    fun readsAnswerAddresses() {
        val query = dnsQuery("example.com")
        val question = Dns.question(query)!!
        val response = java.io.ByteArrayOutputStream().apply {
            write(query, 0, question.endOffset)
            // name pointer to offset 12, type A, class IN, TTL, length 4, 93.184.216.34
            write(byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 93, 184.toByte(), 216.toByte(), 34))
        }.toByteArray()
        Packets.put16(response, 6, 1)
        assertEquals(listOf("93.184.216.34"), Dns.answers(response))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(Packets.parseIpv4(ByteArray(10), 10))
        assertNull(Dns.question(ByteArray(8)))
        assertNotNull(Dns.question(dnsQuery("a.example")))
    }
}
