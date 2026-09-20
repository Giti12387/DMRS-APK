package com.apps.apkstore.utils

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import java.io.File
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.MessageDigest
import java.util.jar.JarFile

data class ApkVerificationResult(
    val isValid: Boolean,
    val errorMessage: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val versionCode: Int = 0,
    val minSdk: Int = 0,
    val targetSdk: Int = 0,
    val permissions: List<String> = emptyList(),
    val certificates: List<Certificate> = emptyList(),
    val signatureHash: String = "",
    val icon: Drawable? = null,
    val label: String = ""
)

object ApkUtils {

    private const val TAG = "ApkUtils"

    fun verifyApk(context: Context, apkFile: File, bypassSignatureCheck: Boolean = false): ApkVerificationResult {
        return try {
            if (!apkFile.exists()) {
                return ApkVerificationResult(false, "APK file does not exist")
            }
            if (!apkFile.canRead()) {
                return ApkVerificationResult(false, "Cannot read APK file")
            }

            val packageInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
                ?: return ApkVerificationResult(false, "Failed to parse APK")

            val packageName = packageInfo.packageName ?: ""
            val versionName = packageInfo.versionName ?: "1.0"
            @Suppress("DEPRECATION")
            val versionCode = packageInfo.versionCode

            val certs = getCertificates(apkFile)
            val signatureHash = calculateSignatureHash(certs)

            val label = try {
                packageInfo.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: ""
            } catch (e: Exception) {
                ""
            }

            val icon = try {
                packageInfo.applicationInfo?.loadIcon(context.packageManager)
            } catch (e: Exception) {
                null
            }

            ApkVerificationResult(
                isValid = true,
                packageName = packageName,
                versionName = versionName,
                versionCode = versionCode,
                permissions = packageInfo.requestedPermissions?.toList() ?: emptyList(),
                certificates = certs,
                signatureHash = signatureHash,
                icon = icon,
                label = label
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying APK", e)
            ApkVerificationResult(false, "Verification error: ${e.message}")
        }
    }

    private fun getCertificates(apkFile: File): List<Certificate> {
        val certs = mutableListOf<Certificate>()
        val jarFile = JarFile(apkFile)
        try {
            val entries = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC")) {
                    val inputStream = jarFile.getInputStream(entry)
                    val cf = CertificateFactory.getInstance("X.509")
                    val cert = cf.generateCertificate(inputStream)
                    certs.add(cert)
                    inputStream.close()
                }
            }
        } finally {
            jarFile.close()
        }
        return certs
    }

    private fun calculateSignatureHash(certificates: List<Certificate>): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            certificates.forEach { cert ->
                digest.update(cert.encoded)
            }
            bytesToHex(digest.digest())
        } catch (e: Exception) {
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun getApkInfo(context: Context, apkFile: File): PackageInfo? {
        return try {
            context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_ACTIVITIES)
        } catch (e: Exception) {
            null
        }
    }

    fun isApkSigned(apkFile: File): Boolean {
        return try {
            val jarFile = JarFile(apkFile)
            val entries = jarFile.entries()
            var hasSignature = false
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.name.startsWith("META-INF/") &&
                    (entry.name.endsWith(".RSA") || entry.name.endsWith(".DSA") || entry.name.endsWith(".EC"))) {
                    hasSignature = true
                    break
                }
            }
            jarFile.close()
            hasSignature
        } catch (e: Exception) {
            false
        }
    }

    fun extractApkIcon(context: Context, apkFile: File): Drawable? {
        val packageInfo = getApkInfo(context, apkFile)
        return packageInfo?.applicationInfo?.loadIcon(context.packageManager)
    }
}