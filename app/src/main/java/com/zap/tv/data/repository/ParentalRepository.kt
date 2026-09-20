package com.zap.tv.data.repository

import com.zap.tv.data.local.dao.ParentalPinDao
import com.zap.tv.data.local.entity.ParentalPinEntity
import com.zap.tv.util.ParentalUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentalRepository @Inject constructor(private val dao: ParentalPinDao) {
    suspend fun isLockedFor(channelIsNsfw: Boolean): Boolean {
        if (!channelIsNsfw) return false
        val pin = dao.get() ?: return false
        if (pin.pinHash.isNullOrEmpty()) return false
        if (pin.lockedUntilMs > System.currentTimeMillis()) return true
        return true // pin exists so channel requires auth
    }
    suspend fun hasPin(): Boolean = !dao.get()?.pinHash.isNullOrEmpty()
    suspend fun verify(pin:String): Boolean {
        val ent = dao.get() ?: return true
        val hash = ent.pinHash ?: return true
        if (ent.lockedUntilMs > System.currentTimeMillis()) return false
        val ok = ParentalUtils.verify(pin, hash)
        if (ok) {
            dao.save(ent.copy(failedAttempts = 0, lockedUntilMs = 0))
        } else {
            val fails = ent.failedAttempts + 1
            val lock = if (fails >=3) System.currentTimeMillis()+ 5*60*1000 else 0
            dao.save(ent.copy(failedAttempts = fails, lockedUntilMs = lock))
        }
        return ok
    }
    suspend fun isCurrentlyLocked(): Boolean {
        val e = dao.get() ?: return false
        return e.lockedUntilMs > System.currentTimeMillis()
    }
    suspend fun remainingLockMs(): Long {
        val e = dao.get() ?: return 0
        return (e.lockedUntilMs - System.currentTimeMillis()).coerceAtLeast(0)
    }
    suspend fun resetLock(){ dao.get()?.let{ dao.save(it.copy(failedAttempts = 0, lockedUntilMs = 0)) } }
}
