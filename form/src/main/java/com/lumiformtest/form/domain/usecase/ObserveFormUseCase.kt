package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.repository.FormRepository
import javax.inject.Inject

internal class ObserveFormUseCase @Inject constructor(private val repository: FormRepository) {
    operator fun invoke() = repository.observeForm()
}
