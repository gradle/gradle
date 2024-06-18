package org.gradle.internal.declarativedsl.analysis

import org.gradle.declarative.dsl.evaluation.OperationGenerationId
import org.gradle.declarative.dsl.schema.DataProperty
import org.gradle.declarative.dsl.schema.DataType
import org.gradle.declarative.dsl.schema.FqName
import org.gradle.internal.declarativedsl.language.Expr
import org.gradle.internal.declarativedsl.language.FunctionCall
import org.gradle.internal.declarativedsl.language.LanguageTreeElement
import org.gradle.internal.declarativedsl.language.LocalValue


data class ResolutionResult(
    val topLevelReceiver: ObjectOrigin.TopLevelReceiver,
    val assignments: List<AssignmentRecord>,
    val additions: List<DataAdditionRecord>,
    val nestedObjectAccess: List<NestedObjectAccessRecord>,
    val errors: List<ResolutionError>,
    val conventionAssignments: List<AssignmentRecord> = emptyList(),
    val conventionAdditions: List<DataAdditionRecord> = emptyList(),
    val conventionNestedObjectAccess: List<NestedObjectAccessRecord> = emptyList()
)


data class DataAdditionRecord(val container: ObjectOrigin, val dataObject: ObjectOrigin)


data class NestedObjectAccessRecord(val container: ObjectOrigin, val dataObject: ObjectOrigin.AccessAndConfigureReceiver)


data class ResolutionError(
    val element: LanguageTreeElement,
    val errorReason: ErrorReason
)


sealed interface ErrorReason {
    data class AmbiguousImport(val fqName: FqName) : ErrorReason
    data class UnresolvedReference(val reference: Expr) : ErrorReason
    data class NonReadableProperty(val property: DataProperty) : ErrorReason
    data class ReadOnlyPropertyAssignment(val property: DataProperty) : ErrorReason
    data class UnresolvedFunctionCallArguments(val functionCall: FunctionCall) : ErrorReason
    data class UnresolvedFunctionCallReceiver(val functionCall: FunctionCall) : ErrorReason
    data class UnresolvedFunctionCallSignature(val functionCall: FunctionCall) : ErrorReason
    data class AmbiguousFunctions(val functions: List<FunctionCallResolver.FunctionResolutionAndBinding>) : ErrorReason
    data class ValReassignment(val localVal: LocalValue) : ErrorReason
    data class ExternalReassignment(val external: ObjectOrigin.External) : ErrorReason
    data class AssignmentTypeMismatch(val expected: DataType, val actual: DataType) : ErrorReason

    // TODO: these two are never reported for now, instead it is UnresolvedFunctionCallSignature
    data object UnusedConfigureLambda : ErrorReason
    data object MissingConfigureLambda : ErrorReason

    data object AccessOnCurrentReceiverOnlyViolation : ErrorReason
    data class DuplicateLocalValue(val name: String) : ErrorReason
    data object UnresolvedAssignmentLhs : ErrorReason // TODO: report candidate with rejection reasons
    data object UnresolvedAssignmentRhs : ErrorReason // TODO: resolution trace here, too?
    data object UnitAssignment : ErrorReason
    data object DanglingPureExpression : ErrorReason
}


class DefaultOperationGenerationId(override val ordinal: Int) : OperationGenerationId {
    companion object {
        val preExisting = DefaultOperationGenerationId(-1)
        val convention = DefaultOperationGenerationId(0)
        val finalEvaluation = DefaultOperationGenerationId(1)
    }

    override fun compareTo(other: OperationGenerationId): Int = compareValues(ordinal, other.ordinal)
}
