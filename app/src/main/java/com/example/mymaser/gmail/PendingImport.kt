package com.example.mymaser.gmail

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One email the importer already looked at, keyed by Gmail's message id so it's never read twice.
 * [entriesJson] holds what was found, see [com.example.mymaser.ai.entriesToJson].
 */
@Entity
data class PendingImport(
    @PrimaryKey val messageId: String,
    val subject: String,
    val entriesJson: String,
    val createdAt: Long,
    val status: String
) {
    companion object {
        /** Found entries the user hasn't reviewed yet. */
        const val PENDING = "pending"

        /** Nothing to add, or already reviewed. */
        const val DONE = "done"
    }
}

@Dao
interface PendingImportDao : PendingImportStore {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    override suspend fun insert(item: PendingImport)

    @Query("SELECT messageId FROM PendingImport WHERE messageId IN (:ids)")
    override suspend fun knownIds(ids: List<String>): List<String>

    @Query("SELECT * FROM PendingImport WHERE status = '${PendingImport.PENDING}' ORDER BY createdAt")
    fun observePending(): Flow<List<PendingImport>>

    @Query("UPDATE PendingImport SET status = '${PendingImport.DONE}' WHERE messageId IN (:ids)")
    suspend fun markDone(ids: List<String>)
}
