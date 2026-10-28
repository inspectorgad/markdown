package com.example.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [Player::class, Game::class, StatLine::class],
    version = 3,
    exportSchema = false
)
@ConstructedBy(DiamondsDatabaseConstructor::class)
abstract class DiamondsDatabase : RoomDatabase() {
    abstract fun dao(): DiamondsDao

    // Each phone opens the database its own way: DiamondsDatabase.get(context)
    // on Android (androidMain), createDatabase() on iPhone (iosMain).
    companion object
}

/** Builds the database class on every platform; Room's compiler writes each platform's version. */
@Suppress("KotlinNoActualForExpect")
expect object DiamondsDatabaseConstructor : RoomDatabaseConstructor<DiamondsDatabase> {
    override fun initialize(): DiamondsDatabase
}
