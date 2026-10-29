package com.lumiformtest.form.domain.repository

import com.lumiformtest.form.domain.model.FormSnapshot
import com.lumiformtest.form.domain.model.RefreshFormResult
import kotlinx.coroutines.flow.Flow

internal interface FormRepository {
    fun observeForm(): Flow<FormSnapshot?>
    suspend fun refreshForm(): RefreshFormResult
}
