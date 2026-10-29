package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.repository.FormRepository
import com.lumiformtest.form.domain.model.FormObservation
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class ObserveFormUseCase @Inject constructor(private val repository: FormRepository) {
    operator fun invoke(): Flow<FormObservation> = repository.observeForm()
}
