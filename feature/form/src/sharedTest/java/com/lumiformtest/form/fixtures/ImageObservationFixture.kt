package com.lumiformtest.form.fixtures

import com.lumiformtest.form.domain.model.FormImage
import com.lumiformtest.form.domain.model.FormItem
import com.lumiformtest.form.domain.model.FormObservation
import com.lumiformtest.form.domain.model.ImageObservation

// Fake repositories reuse form fixtures; production observes one saved image row.
internal fun FormObservation.toImageObservation(imageId: Long): ImageObservation = when (this) {
    FormObservation.ReadFailure -> ImageObservation.ReadFailure
    is FormObservation.Snapshot -> {
        fun find(items: List<FormItem>): FormImage? = items.firstNotNullOfOrNull {
            when (it) {
                is FormItem.Image -> if (it.id == imageId) FormImage(it.id, it.title, it.imageUrl) else null
                is FormItem.Section -> find(it.items)
                else -> null
            }
        }
        ImageObservation.Content(value?.document?.pages?.firstNotNullOfOrNull { find(it.items) })
    }
}
