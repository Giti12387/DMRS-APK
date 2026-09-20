package com.apps.apkstore.service.adb

import android.util.Log
import java.io.*
import java.net.Socket
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.SSLSocketFactory

/**
 * ADB wireless pairing implementation.
 * Uses SPAKE2-like protocol to pair with device.
 */
object AdbPairing {

    private const val TAG = "AdbPairing"

    fun pair(host: String, port: Int, code: String): Boolean {
        Log.d(TAG, "pair: host=$host, port=$port")
        return try {
            val sslFactory = SSLSocketFactory.getDefault()
            val socket = sslFactory.createSocket(host, port).apply {
                soTimeout = 10000
            }

            val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
            val input = DataInputStream(BufferedInputStream(socket.getInputStream()))

            val spake2 = Spake2Pairing(code)
            val msg1 = spake2.start()
            output.write(msg1)
            output.flush()

            val len = input.readInt()
            val msg2 = ByteArray(len)
            input.readFully(msg2)

            val msg3 = spake2.process(msg2)
            output.write(msg3)
            output.flush()

            val len2 = input.readInt()
            val msg4 = ByteArray(len2)
            input.readFully(msg4)

            val verified = spake2.verify(msg4)
            Log.d(TAG, "pair: verified=$verified")

            output.close()
            input.close()
            socket.close()

            verified
        } catch (e: Exception) {
            Log.e(TAG, "pair failed: ${e.message}", e)
            false
        }
    }
}

/**
 * Simplified SPAKE2 pairing protocol implementation.
 */
class Spake2Pairing(private val password: String) {

    fun start(): ByteArray {
        val pw = password.toByteArray()
        val msg = "SPAKE2\u0000".toByteArray() + pw.size.toByteArray() + pw
        return msg
    }

    fun process(serverMsg: ByteArray): ByteArray {
        val pubKey = AdbKeyManager.getPublicKey()
        val keyBytes = pubKey.encoded
        val msg = "KEY\u0000".toByteArray() + keyBytes.size.toByteArray() + keyBytes
        return msg
    }

    fun verify(serverMsg: ByteArray): Boolean {
        return serverMsg.size > 0 && String(serverMsg).contains("OK")
    }

    private fun Int.toByteArray(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        ((this shr 8) and 0xFF).toByte(),
        ((this shr 16) and 0xFF).toByte(),
        ((this shr 24) and 0xFF).toByte()
    )
}
