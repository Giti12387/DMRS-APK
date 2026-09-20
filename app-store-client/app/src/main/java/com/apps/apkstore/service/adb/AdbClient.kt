package com.apps.apkstore.service.adb

import android.util.Log
import java.io.*
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.util.Random
import javax.net.ssl.SSLSocketFactory

object AdbClient {

    private const val TAG = "AdbClient"

    fun create(host: String, port: Int): AdbConnection {
        return AdbConnection(host, port)
    }

    fun pair(host: String, port: Int, code: String): Boolean {
        return AdbPairing.pair(host, port, code)
    }
}

class AdbConnection(private val host: String, private val port: Int) : Closeable {

    companion object {
        private const val TAG = "AdbConnection"
    }

    private var socket: Socket? = null
    private var output: DataOutputStream? = null
    private var input: DataInputStream? = null

    fun connect(): AdbConnection {
        Log.d(TAG, "Connecting to $host:$port")
        val sslFactory = SSLSocketFactory.getDefault()
        socket = sslFactory.createSocket(host, port).apply {
            soTimeout = 10000
        }
        output = DataOutputStream(BufferedOutputStream(socket!!.getOutputStream()))
        input = DataInputStream(BufferedInputStream(socket!!.getInputStream()))
        authenticate()
        return this
    }

    private fun authenticate() {
        Log.d(TAG, "Authenticating...")
        val keyPair = AdbKeyManager.getOrCreateKeyPair()

        val versionMsg = readMessage()
        Log.d(TAG, "Version: ${String(versionMsg.payload)}")

        sendPacket(AdbPacket(AdbPacket.CMD_AUTH, 0, keyPair.public.encoded))

        val authResponse = readMessage()
        if (authResponse.cmd == AdbPacket.CMD_AUTH && authResponse.arg1 == 2) {
            val signature = AdbKeyManager.sign(keyPair.private, authResponse.payload)
            sendPacket(AdbPacket(AdbPacket.CMD_AUTH, 1, signature))
            val ok = readMessage()
            Log.d(TAG, "Auth result cmd=${ok.cmd}")
        } else if (authResponse.cmd == AdbPacket.CMD_OKAY) {
            Log.d(TAG, "Auth OK")
        } else {
            Log.w(TAG, "Auth failed: cmd=${authResponse.cmd}")
        }
    }

    fun shell(command: String): String {
        Log.d(TAG, "Shell: $command")
        sendPacket(AdbPacket(AdbPacket.CMD_OPEN, 0, "shell:$command\u0000".toByteArray()))
        val ready = readMessage()
        val id = ready.arg0
        val sb = StringBuilder()
        while (true) {
            val msg = readMessage()
            if (msg.cmd == AdbPacket.CMD_WRTE) {
                sb.append(String(msg.payload))
                sendPacket(AdbPacket(AdbPacket.CMD_OKAY, id, ByteArray(0)))
            } else if (msg.cmd == AdbPacket.CMD_CLSE) {
                break
            }
        }
        return sb.toString()
    }

    fun install(apkFile: File): Boolean {
        Log.d(TAG, "Installing: ${apkFile.name} (${apkFile.length()} bytes)")
        val remotePath = "/data/local/tmp/${apkFile.name}"
        pushFile(apkFile, remotePath)
        val result = shell("pm install -r -d -g \"$remotePath\"")
        Log.d(TAG, "Install result: $result")
        shell("rm -f \"$remotePath\"")
        return result.contains("Success")
    }

    private fun pushFile(localFile: File, remotePath: String) {
        Log.d(TAG, "Push: ${localFile.name} -> $remotePath")
        sendPacket(AdbPacket(AdbPacket.CMD_OPEN, 0, "sync:\u0000".toByteArray()))
        readMessage()

        val stat = "STAT$remotePath\u0000".toByteArray()
        output!!.write(stat)
        output!!.flush()

        val recvId = readRawMessage().payload[0].toInt() and 0xFF
        val mode = readInt()
        val size = readInt()
        val mtime = readInt()

        val sendData = "SEND$remotePath\u0000".toByteArray()
        output!!.write(sendData)
        output!!.write(intToBytes(localFile.length().toInt()))
        output!!.write(intToBytes(0))
        output!!.write(intToBytes(localFile.length().toInt()))
        output!!.write(intToBytes(0x3B6))
        output!!.flush()

        val buffer = ByteArray(65536)
        var offset = 0
        localFile.inputStream().use { fis ->
            while (true) {
                val read = fis.read(buffer)
                if (read == -1) break
                val data = "DATA$remotePath\u0000".toByteArray()
                output!!.write(data)
                output!!.write(intToBytes(read))
                output!!.write(buffer, 0, read)
                output!!.flush()
                offset += read
            }
        }

        output!!.write("DONE\u0000".toByteArray())
        output!!.write(intToBytes(0))
        output!!.flush()

        readRawMessage()
        Log.d(TAG, "Push complete: $offset bytes")
    }

    private fun readInt(): Int {
        val b = ByteArray(4)
        input!!.readFully(b)
        return ((b[0].toInt() and 0xFF) or
                ((b[1].toInt() and 0xFF) shl 8) or
                ((b[2].toInt() and 0xFF) shl 16) or
                ((b[3].toInt() and 0xFF) shl 24))
    }

    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            ((value shr 8) and 0xFF).toByte(),
            ((value shr 16) and 0xFF).toByte(),
            ((value shr 24) and 0xFF).toByte()
        )
    }

    private fun readRawMessage(): AdbPacket {
        val cmd = input!!.readInt()
        val arg0 = input!!.readInt()
        val arg1 = input!!.readInt()
        val len = input!!.readInt()
        val check = input!!.readInt()
        val payload = if (len > 0) ByteArray(len) else ByteArray(0)
        if (len > 0) input!!.readFully(payload)
        val msgCheck = input!!.readInt()
        return AdbPacket(cmd, arg0, payload, arg1)
    }

    private fun readMessage(): AdbPacket {
        val cmd = input!!.readInt()
        val arg0 = input!!.readInt()
        val arg1 = input!!.readInt()
        val len = input!!.readInt()
        val check = input!!.readInt()
        val payload = if (len > 0) ByteArray(len) else ByteArray(0)
        if (len > 0) input!!.readFully(payload)
        val msgCheck = input!!.readInt()
        return AdbPacket(cmd, arg0, payload, arg1)
    }

    private fun sendPacket(packet: AdbPacket) {
        val data = packet.toBytes()
        output!!.write(data)
        output!!.flush()
    }

    override fun close() {
        try {
            output?.close()
            input?.close()
            socket?.close()
        } catch (_: Exception) {}
    }
}

data class AdbPacket(
    val cmd: Int,
    val arg0: Int,
    val payload: ByteArray,
    val arg1: Int = 0
) {
    companion object {
        const val CMD_CNXN = 0x4e584e43
        const val CMD_OPEN = 0x4e45504f
        const val CMD_OKAY = 0x59414b4f
        const val CMD_CLSE = 0x45534c43
        const val CMD_WRTE = 0x45545257
        const val CMD_AUTH = 0x48545541
        const val CMD_STLS = 0x534c5453
    }

    fun toBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeInt(cmd)
        dos.writeInt(arg0)
        dos.writeInt(arg1)
        dos.writeInt(payload.size)
        var check = 0
        for (b in payload) check += (b.toInt() and 0xFF)
        dos.writeInt(check)
        dos.write(payload)
        dos.writeInt(check xor cmd xor payload.size)
        return baos.toByteArray()
    }
}
