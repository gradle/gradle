package org.gradle.internal.declarativedsl.parsing

import org.gradle.internal.declarativedsl.language.BlockElement
import org.gradle.internal.declarativedsl.language.DataStatement
import org.gradle.internal.declarativedsl.language.Element
import org.gradle.internal.declarativedsl.language.ElementResult
import org.gradle.internal.declarativedsl.language.ErroneousStatement
import org.gradle.internal.declarativedsl.language.FailingResult
import org.gradle.internal.declarativedsl.language.LanguageResult
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.MultipleFailuresResult
import org.gradle.internal.declarativedsl.language.SingleFailureResult
import org.gradle.internal.declarativedsl.language.Syntactic
import org.gradle.internal.declarativedsl.language.SyntacticResult


internal
class FailureCollectorContext {
    private
    val currentFailures: MutableList<FailingResult> = mutableListOf()

    val failures: List<FailingResult>
        get() = currentFailures

    // TODO: introduce a type for a result with definitely collected failure?
    fun <T> checkForFailure(result: SyntacticResult<T>): CheckedResult<SyntacticResult<T>> =
        CheckedResult(collectingFailure(result))

    fun <T : LanguageTreeElement> checkForFailure(result: ElementResult<T>): CheckedResult<ElementResult<T>> =
        CheckedResult(collectingFailure(result))

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

    class CheckBarrierContext {

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
            0 -> evaluate(CheckBarrierContext())
            1 -> currentFailures.single()
            else -> MultipleFailuresResult(currentFailures.flatMap { if (it is MultipleFailuresResult) it.failures else listOf(it as SingleFailureResult) })
        }

    fun <T> syntacticIfNoFailures(evaluate: CheckBarrierContext.() -> SyntacticResult<T>): SyntacticResult<T> =
        when (currentFailures.size) {
            0 -> evaluate(CheckBarrierContext())
            1 -> currentFailures.single()
            else -> MultipleFailuresResult(currentFailures.flatMap { if (it is MultipleFailuresResult) it.failures else listOf(it as SingleFailureResult) })
        }

    class CheckedResult<T : LanguageResult<*>>(val value: T)
}


internal
fun ElementResult<DataStatement>.asBlockElement(): BlockElement = when (this) {
    is Element -> element
    is FailingResult -> ErroneousStatement(this)
}


internal
fun <T : LanguageTreeElement> elementOrFailure(
    evaluate: FailureCollectorContext.() -> ElementResult<T>
): ElementResult<T> {
    val context = FailureCollectorContext()
    return evaluate(context)
}


internal
fun <T> syntacticOrFailure(
    evaluate: FailureCollectorContext.() -> SyntacticResult<T>
): SyntacticResult<T> {
    val context = FailureCollectorContext()
    return evaluate(context)
}
