package com.h0tk3y.kotlin.staticObjectNotation.astToLanguageTree

import com.h0tk3y.kotlin.staticObjectNotation.language.BlockElement
import com.h0tk3y.kotlin.staticObjectNotation.language.DataStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.ErroneousStatement
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement

internal class FailureCollectorContext {
    private val currentFailures: MutableList<FailingResult> = mutableListOf()

    val failures: List<FailingResult> get() = currentFailures

    // TODO: introduce a type for a result with definitely collected failure?
    fun <T> checkForFailure(result: SyntacticResult<T>): CheckedResult<SyntacticResult<T>> =
        CheckedResult(collectingFailure(result))

    fun <T> checkNullableForFailure(result: SyntacticResult<T>?): CheckedResult<SyntacticResult<T>>? =
        if (result == null) null else CheckedResult(collectingFailure(result))

    fun <T : LanguageTreeElement> checkForFailure(result: ElementResult<T>): CheckedResult<ElementResult<T>> =
        CheckedResult(collectingFailure(result))

    fun failNow(failingResult: FailingResult): FailingResult {
        collectingFailure(failingResult)
        return syntacticIfNoFailures<Nothing> { error("expected a failure") } as FailingResult
    }

    fun collectingFailure(maybeFailure: FailingResult?) {
        if (maybeFailure != null) {
            currentFailures.add(maybeFailure)
        }
    }

    fun <T> collectingFailure(result: T): T {
        when (result) {
            is FailingResult -> currentFailures.add(result)
        }
        return result
    }

    interface CheckBarrierContext {

        fun <T : LanguageTreeElement> checked(result: CheckedResult<ElementResult<T>>): T {
            val value = result.value
            check(value is Element<T>)
            return value.element
        }

        fun <T> checked(result: CheckedResult<SyntacticResult<T>>): T {
            val value = result.value
            check(value is Syntactic<T>)
            return value.value
        }

        fun <T> checked(results: List<CheckedResult<SyntacticResult<T>>>): List<T> =
            results.map {
                val syntacticResult = it.value
                check(syntacticResult is Syntactic<T>)
                syntacticResult.value
            }.toList()
    }

    fun <T : LanguageTreeElement> elementIfNoFailures(evaluate: CheckBarrierContext.() -> ElementResult<T>): ElementResult<T> =
        when (currentFailures.size) {
            0 -> evaluate(object :
                CheckBarrierContext {})
            1 -> currentFailures.single()
            else -> MultipleFailuresResult(currentFailures.flatMap { if (it is MultipleFailuresResult) it.failures else listOf(it as SingleFailureResult) })
        }

    fun <T> syntacticIfNoFailures(evaluate: CheckBarrierContext.() -> SyntacticResult<T>): SyntacticResult<T> =
        when (currentFailures.size) {
            0 -> evaluate(object :
                CheckBarrierContext {})
            1 -> currentFailures.single()
            else -> MultipleFailuresResult(currentFailures.flatMap { if (it is MultipleFailuresResult) it.failures else listOf(it as SingleFailureResult) })
        }

    class CheckedResult<T : LanguageResult<*>>(val value: T)
}

internal fun ElementResult<DataStatement>.asBlockElement(): BlockElement = when (this) {
    is Element -> element
    is FailingResult -> ErroneousStatement(this)
}


internal fun <T : LanguageTreeElement> elementOrFailure(
    evaluate: FailureCollectorContext.() -> ElementResult<T>
): ElementResult<T> {
    val context = FailureCollectorContext()
    return evaluate(context)
}

internal fun <T> syntacticOrFailure(
    evaluate: FailureCollectorContext.() -> SyntacticResult<T>
): SyntacticResult<T> {
    val context = FailureCollectorContext()
    return evaluate(context)
}
