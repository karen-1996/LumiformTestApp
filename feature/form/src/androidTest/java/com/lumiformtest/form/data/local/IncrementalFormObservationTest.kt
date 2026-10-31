package com.lumiformtest.form.data.local

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.FormDocument
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormPage
import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.ResponseOption
import com.lumiformtest.form.domain.model.ResponseSet
import com.lumiformtest.form.presentation.screens.form.mapper.toUiModel
import java.time.Instant
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncrementalFormObservationTest {
    @Test
    fun answersDoNotReloadDefinitionsAndSameTimestampRefreshesStayCoherent() = runBlocking {
        val queries = Channel<Unit>(Channel.UNLIMITED)
        val database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, FormDatabase::class.java)
            .setQueryCallback({ sql, _ ->
                if (sql.contains("FROM `form_node` WHERE `snapshotId` IN") || sql.contains("FROM form_node WHERE snapshotId IN")) queries.trySend(Unit)
            }, Executor { it.run() }).build()
        val local = RoomFormLocalDataSource(database.formDao(), Dispatchers.IO)
        val emitted = Channel<FormSnapshot>(Channel.UNLIMITED)
        val time = Instant.EPOCH
        local.replaceForm(document("Original", true), time)
        val observer = launch(Dispatchers.Default) { local.observeForm().collect { it?.let { snapshot -> emitted.send(snapshot) } } }
        try {
            val initial = withTimeout(5_000) { emitted.receive() }
            withTimeout(5_000) { queries.receive() }
            while (queries.tryReceive().isSuccess) Unit
            local.toggleChoiceOption(ChoiceOption(10, 100, 1))
            val answered = withTimeout(5_000) { emitted.receive() }
            assertSame(initial.document, answered.document)
            assertEquals(initial.definitionVersion, answered.definitionVersion)
            assertEquals(setOf(1L), answered.selections[10]?.optionIds)
            assertNull("An answer must not query all form nodes", queries.tryReceive().getOrNull())
            val originalUi = initial.toUiModel()
            val answeredUi = answered.toUiModel(originalUi)
            assertSame(originalUi.rowIndexById, answeredUi.rowIndexById)
            local.replaceForm(document("Changed at the same time", false), time)
            val refreshed = withTimeout(5_000) { emitted.receive() }
            assertNotEquals(answered.definitionVersion, refreshed.definitionVersion)
            assertEquals(time, refreshed.lastUpdated)
            assertEquals("Changed at the same time", refreshed.document.pages.single().title)
            assertEquals(false, (refreshed.document.pages.single().items.single() as FormItem.Choice).responseSet.allowsMultipleSelection)
            assertEquals(setOf(1L), refreshed.selections[10]?.optionIds)
            val refreshedUi = refreshed.toUiModel(answeredUi)
            assertEquals("Changed at the same time", (refreshedUi.rows[0] as com.lumiformtest.form.presentation.screens.form.model.FormRowUiModel.Page).title)
        } finally { observer.cancelAndJoin(); database.close(); queries.close(); emitted.close() }
    }

    private fun document(title: String, multiple: Boolean) = FormDocument(listOf(FormPage(1, title, listOf(
        FormItem.Choice(10, "Choice", ResponseSet(100, multiple, listOf(ResponseOption(1, "One", null))))
    ))))
}
