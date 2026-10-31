package com.lumiformtest.form.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2: Migration by lazy {
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_form_node_id_responseSetId ON form_node (id, responseSetId)")
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS selected_response (
                    questionId INTEGER NOT NULL, responseSetId INTEGER NOT NULL,
                    optionId INTEGER NOT NULL, snapshotId INTEGER NOT NULL,
                    PRIMARY KEY(questionId, responseSetId, optionId),
                    FOREIGN KEY(questionId, responseSetId) REFERENCES form_node(id, responseSetId) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(responseSetId, optionId) REFERENCES response_option(responseSetId, id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(snapshotId) REFERENCES form_metadata(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())
            db.execSQL("CREATE INDEX IF NOT EXISTS index_selected_response_responseSetId_optionId ON selected_response (responseSetId, optionId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_selected_response_snapshotId ON selected_response (snapshotId)")
        }
    }
}

internal val MIGRATION_2_3: Migration by lazy {
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE form_metadata ADD COLUMN definitionVersion INTEGER NOT NULL DEFAULT 0")
        }
    }
}
