package com.geeksville.mesh.util

import java.security.MessageDigest

object Crypto {

    fun sha256Digest(text: String): ByteArray {
        val bytes = text.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(bytes)
    }
}