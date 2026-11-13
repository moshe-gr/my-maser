package com.example.mymaser.history

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HistoryDb::class.java)

    @Test
    fun taxOnlyDonationsCountForTaxCreditAfterUpgrade() {
        helper.createDatabase(DB, 7).use { db ->
            db.execSQL(
                "INSERT INTO History(timeStamp, name, amount, isDonation, maserPercentage, currency, isTaxOnly) VALUES " +
                    "(1, 'Salary', 1000, 0, 10, 'ILS', 0), (2, 'Fund', 100, 1, 10, 'ILS', 0), " +
                    "(3, 'Hospital', 500, 1, 10, 'ILS', 1)"
            )
        }
        val db = helper.runMigrationsAndValidate(DB, 8, true)
        val flags = db.query("SELECT name, taxCredit FROM History ORDER BY timeStamp").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getInt(1)) }
        }
        assertEquals(listOf("Salary" to 0, "Fund" to 0, "Hospital" to 1), flags)
    }

    private companion object {
        const val DB = "migration-test"
    }
}
