package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [Player::class, Game::class, StatLine::class],
    version = 2,
    exportSchema = false
)
abstract class DiamondsDatabase : RoomDatabase() {
    abstract fun dao(): DiamondsDao

    companion object {
        @Volatile
        private var instance: DiamondsDatabase? = null

        fun get(context: Context): DiamondsDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    DiamondsDatabase::class.java,
                    "kc_diamonds.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
