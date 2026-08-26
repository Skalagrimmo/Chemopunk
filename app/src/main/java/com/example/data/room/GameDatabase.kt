package com.example.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        InventoryItemEntity::class,
        CharacterProfileEntity::class,
        PerkEntity::class,
        NpcShopEntity::class,
        StoryScriptEntity::class,
        NarrativeNodeEntity::class,
        BranchingChoiceEntity::class,
        NarrativeProgressEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class GameDatabase : RoomDatabase() {

    abstract fun inventoryDao(): InventoryDao
    abstract fun characterProfileDao(): CharacterProfileDao
    abstract fun perkDao(): PerkDao
    abstract fun shopDao(): ShopDao
    abstract fun storyNarrativeDao(): StoryNarrativeDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "chemopank_game_database.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
