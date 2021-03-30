package net.inqer.touringapp.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.inqer.touringapp.data.models.TourRouteBrief

@Dao
interface TourRouteBriefDao : BaseDao<TourRouteBrief> {
    @Query("SELECT * FROM routes_brief")
    fun getRoutesFlow(): Flow<List<TourRouteBrief>>
}