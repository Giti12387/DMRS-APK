package com.apps.apkstore.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.apps.apkstore.data.model.ReviewModel
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Flowable

@Dao
interface ReviewDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReview(review: ReviewModel): Completable

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertReviews(reviews: List<ReviewModel>): Completable

    @Query("SELECT * FROM reviews WHERE appId = :appId ORDER BY date DESC LIMIT :limit")
    fun getReviewsByAppId(appId: String, limit: Int): Flowable<List<ReviewModel>>

    @Query("DELETE FROM reviews WHERE appId = :appId")
    fun deleteReviewsByAppId(appId: String): Completable
}
