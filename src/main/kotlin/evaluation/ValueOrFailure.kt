package com.h0tk3y.kotlin.staticObjectNotation.evaluation

import com.h0tk3y.kotlin.staticObjectNotation.LanguageTreeElement

sealed interface ValueOrFailure {
    val originElement: LanguageTreeElement

    data class Value<T>(val value: T, override val originElement: LanguageTreeElement) : ValueOrFailure
    sealed interface ValueFailure : ValueOrFailure
}
