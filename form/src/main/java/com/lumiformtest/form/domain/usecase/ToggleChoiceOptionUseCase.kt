package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.model.ChoiceOption
import com.lumiformtest.form.domain.model.ChoiceSelectionResult
import com.lumiformtest.form.domain.repository.FormRepository
import javax.inject.Inject

internal class ToggleChoiceOptionUseCase @Inject constructor(private val repository: FormRepository) {
    suspend operator fun invoke(option: ChoiceOption): ChoiceSelectionResult = repository.toggleChoiceOption(option)
}
