package com.example.domain

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TotpHelper {
    private const val BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generateSecretBase32(bytesLen: Int = 20): String {
        val rng = SecureRandom()
        val out = StringBuilder()
        val bytes = ByteArray(bytesLen)
        rng.nextBytes(bytes)
        var buffer = 0
        var bitsLeft = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                val index = (buffer shr (bitsLeft - 5)) and 0x1F
                bitsLeft -= 5
                out.append(BASE32_CHARS[index])
            }
        }
        if (bitsLeft > 0) {
            val index = (buffer shl (5 - bitsLeft)) and 0x1F
            out.append(BASE32_CHARS[index])
        }
        return out.toString()
    }

    private fun decodeBase32(secret: String): ByteArray {
        val clean = secret.uppercase().replace("=", "").replace(" ", "")
        val out = mutableListOf<Byte>()
        var buffer = 0
        var bitsLeft = 0
        for (ch in clean) {
            val v = BASE32_CHARS.indexOf(ch)
            if (v < 0) continue
            buffer = (buffer shl 5) or v
            bitsLeft += 5
            if (bitsLeft >= 8) {
                out.add(((buffer shr (bitsLeft - 8)) and 0xFF).toByte())
                bitsLeft -= 8
            }
        }
        return out.toByteArray()
    }

    fun currentCode(secretBase32: String, stepOffset: Long = 0L, nowMs: Long = System.currentTimeMillis()): String {
        val key = decodeBase32(secretBase32)
        if (key.isEmpty()) return "000000"
        val counter = (nowMs / 1000L) / 30L + stepOffset
        val msg = ByteBuffer.allocate(8).putLong(counter).array()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        val otp = binary % 1_000_000
        return "%06d".format(otp)
    }

    fun verifyTotp(secretBase32: String, code: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val clean = code.trim()
        if (clean.length != 6 || !clean.all { it.isDigit() }) return false
        return (-1L..1L).any { offset -> currentCode(secretBase32, offset, nowMs) == clean }
    }

    fun generateBackupCodes(count: Int = 8): List<String> {
        val rng = SecureRandom()
        val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return List(count) {
            val part1 = (1..4).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
            val part2 = (1..4).map { alphabet[rng.nextInt(alphabet.length)] }.joinToString("")
            "$part1-$part2"
        }
    }
}
