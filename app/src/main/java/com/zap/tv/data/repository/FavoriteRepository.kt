package com.zap.tv.data.repository

import com.zap.tv.data.local.dao.FavoriteDao
import com.zap.tv.data.local.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavoriteRepository @Inject constructor(private val dao: FavoriteDao) {
    fun observe(): Flow<List<FavoriteEntity>> = dao.observeAll()
    suspend fun isFavorite(n:Int) = dao.isFavorite(n)
    suspend fun toggle(n:Int): Boolean {
        return if (dao.isFavorite(n)) { dao.remove(n); false } else { dao.add(FavoriteEntity(n)); true }
    }
    suspend fun getAll() = dao.getAll()
}
