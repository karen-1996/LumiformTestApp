package com.lumiformtest.form.domain.model

internal sealed interface FormObservation {
    data class Snapshot(val value: FormSnapshot?) : FormObservation
    data object ReadFailure : FormObservation
}
