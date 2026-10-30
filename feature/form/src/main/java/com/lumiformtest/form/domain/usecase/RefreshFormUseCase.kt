package com.lumiformtest.form.domain.usecase

import com.lumiformtest.form.domain.repository.FormRepository
import javax.inject.Inject

internal class RefreshFormUseCase @Inject constructor(private val repository: FormRepository) {
    suspend operator fun invoke() = repository.refreshForm()
}
