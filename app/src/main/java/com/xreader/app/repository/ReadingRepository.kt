package com.xreader.app.repository

import com.xreader.app.data.ReadingDao
import com.xreader.app.data.ReadingSessionEntity
import com.xreader.app.data.ReadingStateEntity
import kotlinx.coroutines.flow.Flow

class ReadingRepository(private val dao: ReadingDao) {
    fun observeState(bookId: Long): Flow<ReadingStateEntity?> = dao.observeState(bookId)
    fun observeStates(): Flow<List<ReadingStateEntity>> = dao.observeStates()
    suspend fun getState(bookId: Long): ReadingStateEntity? = dao.getState(bookId)
    suspend fun saveState(state: ReadingStateEntity) = dao.upsertState(state)
    suspend fun insertSession(session: ReadingSessionEntity): Long = dao.insertSession(session)
    fun observeSessions(): Flow<List<ReadingSessionEntity>> = dao.observeSessions()
    fun observeSessionsForBook(bookId: Long): Flow<List<ReadingSessionEntity>> =
        dao.observeSessionsForBook(bookId)
}
