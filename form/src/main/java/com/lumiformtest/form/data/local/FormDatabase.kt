package com.lumiformtest.form.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lumiformtest.form.data.local.entity.FormMetadataEntity
import com.lumiformtest.form.data.local.entity.FormNodeEntity
import com.lumiformtest.form.data.local.entity.ResponseOptionEntity
import com.lumiformtest.form.data.local.entity.ResponseSetEntity

@Database(
    entities = [FormMetadataEntity::class, FormNodeEntity::class, ResponseSetEntity::class, ResponseOptionEntity::class],
    version = 1,
    exportSchema = true
)
internal abstract class FormDatabase : RoomDatabase() {
    abstract fun formDao(): FormDao
}
