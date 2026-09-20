package com.apps.apkstore.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.apps.apkstore.data.model.AppModel
import com.apps.apkstore.data.model.CategoryModel
import com.apps.apkstore.data.model.ReviewModel

@Database(
    entities = [
        AppModel::class,
        CategoryModel::class,
        ReviewModel::class,
        DownloadEntity::class,
        InstalledAppEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun categoryDao(): CategoryDao
    abstract fun reviewDao(): ReviewDao
    abstract fun downloadDao(): DownloadDao
    abstract fun installedAppDao(): InstalledAppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "zoro_app_store.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}