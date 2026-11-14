package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        RiskRegisterEntity::class,
        ComplianceSnapshotEntity::class,
        ControlMonitorEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(AdwaTypeConverters::class)
abstract class AdwaDatabase : RoomDatabase() {
    abstract fun riskRegisterDao(): RiskRegisterDao
    abstract fun complianceDataDao(): ComplianceDataDao

    companion object {
        @Volatile
        private var INSTANCE: AdwaDatabase? = null

        fun getInstance(context: Context): AdwaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdwaDatabase::class.java,
                    "adwa_compliance_risk.db",
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
