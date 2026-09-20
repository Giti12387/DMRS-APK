package com.apps.apkstore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.apps.apkstore.data.model.CategoryModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategory(category: CategoryModel): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertCategories(categories: List<CategoryModel>): Completable

    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getAllCategories(): Flowable<List<CategoryModel>>

    @Query("SELECT * FROM categories WHERE id = :id")
    fun getCategoryById(id: String): Single<CategoryModel>

    @Query("SELECT * FROM categories WHERE name = :name")
    fun getCategoryByName(name: String): Single<CategoryModel>

    @Query("DELETE FROM categories")
    fun clearCategories(): Completable

    @Query("SELECT * FROM categories ORDER BY `order` ASC")
    fun getAllCategoriesSync(): List<CategoryModel>
}