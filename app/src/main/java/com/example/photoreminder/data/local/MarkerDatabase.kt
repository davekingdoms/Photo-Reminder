package com.example.photoreminder.data.local    // stesso package delle altre classi

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [MarkerEntity::class], version = 1, exportSchema = false
)
@TypeConverters(MarkerConverters::class)
abstract class MarkerDatabase : RoomDatabase() {

    abstract fun markerDao(): MarkerDao

    companion object {
        @Volatile
        private var INSTANCE: MarkerDatabase? = null

        fun getDatabase(context: Context): MarkerDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarkerDatabase::class.java,
                    "marker_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
