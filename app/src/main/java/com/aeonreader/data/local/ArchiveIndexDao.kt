package com.aeonreader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * The full-text-ish search index built from every section and subtopic feed.
 * Aeon has no search API, so this table is what makes searching beyond the
 * newest 20 essays per section possible at all.
 */
@Dao
interface ArchiveIndexDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<ArchiveIndexEntity>)

    @Query("DELETE FROM archive_index")
    suspend fun clear()

    /**
     * Replace wholesale rather than merge: a refresh that only added rows would
     * keep essays the site has since retired.
     */
    @Transaction
    suspend fun replaceAll(rows: List<ArchiveIndexEntity>) {
        clear()
        insertAll(rows)
    }

    @Query("SELECT COUNT(*) FROM archive_index")
    suspend fun count(): Int

    @Query("SELECT MAX(indexedAt) FROM archive_index")
    suspend fun lastIndexedAt(): Long?

    /**
     * All terms must appear somewhere in the row, so a multi-word query narrows
     * rather than widens. LIKE is the right tool here: the corpus is ~1200 rows
     * and this stays well under a millisecond, so an FTS table would be a
     * rebuild-every-release liability for no measurable gain.
     *
     * `ESCAPE` is required, not decorative: the caller escapes user-typed `%` and
     * `_`, and without the clause the backslash would be matched literally and
     * every search would return nothing.
     */
    @Query(
        """
        SELECT * FROM archive_index
        WHERE (:termCount = 0)
           OR (
                (:t0 = '' OR LOWER(title) LIKE '%' || :t0 || '%' ESCAPE '\\' OR LOWER(IFNULL(description,'')) LIKE '%' || :t0 || '%' ESCAPE '\\' OR LOWER(IFNULL(author,'')) LIKE '%' || :t0 || '%' ESCAPE '\\')
            AND (:t1 = '' OR LOWER(title) LIKE '%' || :t1 || '%' ESCAPE '\\' OR LOWER(IFNULL(description,'')) LIKE '%' || :t1 || '%' ESCAPE '\\' OR LOWER(IFNULL(author,'')) LIKE '%' || :t1 || '%' ESCAPE '\\')
            AND (:t2 = '' OR LOWER(title) LIKE '%' || :t2 || '%' ESCAPE '\\' OR LOWER(IFNULL(description,'')) LIKE '%' || :t2 || '%' ESCAPE '\\' OR LOWER(IFNULL(author,'')) LIKE '%' || :t2 || '%' ESCAPE '\\')
            AND (:t3 = '' OR LOWER(title) LIKE '%' || :t3 || '%' ESCAPE '\\' OR LOWER(IFNULL(description,'')) LIKE '%' || :t3 || '%' ESCAPE '\\' OR LOWER(IFNULL(author,'')) LIKE '%' || :t3 || '%' ESCAPE '\\')
            AND (:t4 = '' OR LOWER(title) LIKE '%' || :t4 || '%' ESCAPE '\\' OR LOWER(IFNULL(description,'')) LIKE '%' || :t4 || '%' ESCAPE '\\' OR LOWER(IFNULL(author,'')) LIKE '%' || :t4 || '%' ESCAPE '\\')
           )
        ORDER BY
            CASE WHEN LOWER(title) LIKE :t0 || '%' ESCAPE '\\' THEN 0 ELSE 1 END,
            LENGTH(title)
        LIMIT :limit
        """
    )
    suspend fun search(
        termCount: Int,
        t0: String, t1: String, t2: String, t3: String, t4: String,
        limit: Int
    ): List<ArchiveIndexEntity>
}
