package com.lumiformtest.form.data.local

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.data.mapper.FormEntityMapper
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FormMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), FormDatabase::class.java)
    private val name = "form-migration-test.db"

    @Before
    fun setUp() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }

    @After
    fun tearDown() { InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(name) }

    @Test
    fun migrationPreservesVersionOneContentAndAllowsDurableAnswers() = runBlocking {
        helper.createDatabase(name, 1).apply {
            execSQL("INSERT INTO form_metadata(id,lastUpdatedMillis) VALUES(0,1234)")
            execSQL("INSERT INTO response_set(id,multipleSelection,snapshotId) VALUES(100,0,0)")
            execSQL("INSERT INTO response_option(responseSetId,id,position,label,score) VALUES(100,1,0,'One',NULL)")
            execSQL("""
                INSERT INTO form_node(id,parentId,position,type,title,content,imageUrl,responseSetId,snapshotId)
                VALUES(1,NULL,0,'PAGE','Saved page',NULL,NULL,NULL,0),
                      (10,1,0,'CHOICE',NULL,'Saved question',NULL,100,0)
            """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(name, 2, true, MIGRATION_1_2).close()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun open() = Room.databaseBuilder(context, FormDatabase::class.java, name).addMigrations(MIGRATION_1_2).build()
        var database = open()
        try {
            val local = RoomFormLocalDataSource(database.formDao(), FormEntityMapper(), Dispatchers.IO)
            val snapshot = requireNotNull(local.observeForm().first())
            assertEquals("Saved page", snapshot.document.pages.single().title)
            assertEquals(Instant.ofEpochMilli(1234), snapshot.lastUpdated)
            assertTrue(snapshot.selections.isEmpty())
            assertEquals(ChoiceSelectionResult.Success, local.toggleChoiceOption(ChoiceOption(10, 100, 1)))
        } finally { database.close() }
        database = open()
        try {
            val snapshot = requireNotNull(RoomFormLocalDataSource(database.formDao(), FormEntityMapper(), Dispatchers.IO).observeForm().first())
            assertEquals(setOf(1L), snapshot.selections[10]?.optionIds)
            assertEquals(Instant.ofEpochMilli(1234), snapshot.lastUpdated)
        } finally { database.close() }
    }
}
