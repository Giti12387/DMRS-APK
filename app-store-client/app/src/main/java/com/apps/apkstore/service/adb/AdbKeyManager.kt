package com.apps.apkstore.service.adb

import android.content.Context
import android.util.Log
import java.io.*
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object AdbKeyManager {

    private const val TAG = "AdbKeyManager"
    private const val KEY_FILE_PRIVATE = "adb_key_private"
    private const val KEY_FILE_PUBLIC = "adb_key_public"

    fun getOrCreateKeyPair(): KeyPair {
        val context = com.apps.apkstore.APKStoreApplication.instance
        val prefs = context.getSharedPreferences("adb_keys", Context.MODE_PRIVATE)

        val privB64 = prefs.getString(KEY_FILE_PRIVATE, null)
        val pubB64 = prefs.getString(KEY_FILE_PUBLIC, null)

        if (privB64 != null && pubB64 != null) {
            try {
                val privBytes = Base64.getDecoder().decode(privB64)
                val pubBytes = Base64.getDecoder().decode(pubB64)
                val privKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privBytes))
                val pubKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(pubBytes))
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load keys, generating new: ${e.message}")
            }
        }

        val kpg = KeyPairGenerator.getInstance("RSA")
        kpg.initialize(2048)
        val keyPair = kpg.generateKeyPair()

        prefs.edit()
            .putString(KEY_FILE_PRIVATE, Base64.getEncoder().encodeToString(keyPair.private.encoded))
            .putString(KEY_FILE_PUBLIC, Base64.getEncoder().encodeToString(keyPair.public.encoded))
            .apply()

        Log.d(TAG, "Generated new RSA key pair")
        return keyPair
    }

    fun sign(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA1withRSA")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    fun getPublicKey(): PublicKey {
        return getOrCreateKeyPair().public
    }
}
