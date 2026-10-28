package com.example.data

import android.content.Context
import androidx.room.Room

// Opening the database on Android: the same file, kc_diamonds.db, at the same
// schema version, opened exactly as before. Nothing about an existing install
// changes, so an update finds the phone's own players, games and stat lines
// where it left them.
//
// The app has never carried hand-written migrations: it has always been built
// with fallbackToDestructiveMigration, which rebuilds the tables if the schema
// version ever moves. That is kept as it is, and the version stays at 3, so
// this change moves nothing and wipes nothing. Were a schema change to come
// later, it would want a real migration here before the version is raised -
// an iPhone install starts at the current version and never needs one.

@Volatile
private var instance: DiamondsDatabase? = null

fun DiamondsDatabase.Companion.get(context: Context): DiamondsDatabase =
    instance ?: synchronized(DiamondsDatabase) {
        instance ?: Room.databaseBuilder(
            context.applicationContext,
            DiamondsDatabase::class.java,
            "kc_diamonds.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build().also { instance = it }
    }
