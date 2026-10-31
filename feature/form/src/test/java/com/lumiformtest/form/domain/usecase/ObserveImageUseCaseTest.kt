package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.repository.FormRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveImageUseCaseTest {
    @Test
    fun `image use case forwards identity without observing or traversing the form`() = runTest {
        val expected = ImageObservation.Content(FormImage(9, "Image", "https://example.com/a"))
        val repository = object : FormRepository {
            override fun observeForm(): Flow<FormObservation> = error("A viewer must not read the whole form")
            override fun observeImage(imageId: Long): Flow<ImageObservation> {
                assertEquals(9L, imageId)
                return flowOf(expected)
            }
            override suspend fun refreshForm() = error("Not used")
            override suspend fun toggleChoiceOption(option: ChoiceOption) = error("Not used")
        }
        assertEquals(expected, ObserveImageUseCase(repository)(9).first())
    }
}
