package com.zap.tv.util

import java.security.MessageDigest

object ParentalUtils {
    private val weakPins = setOf("0000","1111","2222","3333","4444","5555","6666","7777","8888","9999","1234","4321","0123")
    fun isWeak(pin: String): Boolean = pin in weakPins || pin.length!=4 || !pin.all { it.isDigit() }
    fun hash(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256")
        return d.digest(pin.toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun verify(pin: String, hash: String): Boolean = hash(pin)==hash
}
