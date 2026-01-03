package com.girlspace.app.ui.innercircle

enum class InnerCircleStep {
    Rules,
    Confirm
}

data class InnerCircleUiState(
    val step: InnerCircleStep = InnerCircleStep.Rules,
    val termsAccepted: Boolean = false,
    val isSaving: Boolean = false,
    val enabled: Boolean = false,
    val error: String? = null
)
