package com.example.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

class AdwaTypeConverters {
    @TypeConverter
    fun fromStringList(values: List<String>?): String {
        val arr = JSONArray()
        values?.forEach { arr.put(it) }
        return arr.toString()
    }

    @TypeConverter
    fun toStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { idx -> arr.getString(idx) }
        }.getOrDefault(emptyList())
    }
}
