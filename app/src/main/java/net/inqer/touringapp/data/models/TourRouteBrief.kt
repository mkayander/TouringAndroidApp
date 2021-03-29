package net.inqer.touringapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName
import java.util.*

@Entity(tableName = "routes_brief")
data class TourRouteBrief(
        @PrimaryKey
        @SerializedName("pk")
        val id: Long,
        val title: String,
        val description: String,
        val image: String,

        @SerializedName("created_at")
        val createdAt: Date,
        @SerializedName("updated_at")
        val updatedAt: Date,
)