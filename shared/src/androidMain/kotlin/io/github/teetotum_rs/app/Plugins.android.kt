package io.github.teetotum_rs.app

import java.security.MessageDigest

actual fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)
