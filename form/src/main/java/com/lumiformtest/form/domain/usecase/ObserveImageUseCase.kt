package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ImageObservation
import com.lumiformtest.form.domain.repository.FormRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class ObserveImageUseCase @Inject constructor(private val repository: FormRepository) {
    operator fun invoke(imageId: Long) = repository.observeForm().map { observation ->
        when (observation) {
            FormObservation.ReadFailure -> ImageObservation.ReadFailure
            is FormObservation.Snapshot -> ImageObservation.Content(
                observation.value?.document?.pages?.firstNotNullOfOrNull { findImage(it.items, imageId) }
            )
        }
    }.distinctUntilChanged()

    private fun findImage(items: List<FormItem>, id: Long): FormImage? = items.firstNotNullOfOrNull {
        when (it) {
            is FormItem.Image -> if (it.id == id) FormImage(it.id, it.title, it.imageUrl) else null
            is FormItem.Section -> findImage(it.items, id)
            else -> null
        }
    }
}
