package com.h0tk3y.kotlin.staticObjectNotation.analysis

import com.h0tk3y.kotlin.staticObjectNotation.language.Expr
import com.h0tk3y.kotlin.staticObjectNotation.language.LanguageTreeElement
import com.h0tk3y.kotlin.staticObjectNotation.language.LocalValue

data class ResolutionResult(
    val topLevelReceiver: ObjectOrigin.TopLevelReceiver,
    val assignments: List<AssignmentRecord>,
    val additions: List<DataAddition>,
    val errors: List<ResolutionError>
)

data class DataAddition(val container: ObjectOrigin, val dataObject: ObjectOrigin)

data class ResolutionError(
    val element: LanguageTreeElement,
    val errorReason: ErrorReason
)

sealed interface ErrorReason {
    data class AmbiguousImport(val fqName: FqName) : ErrorReason
    data class UnresolvedReference(val reference: Expr) : ErrorReason
    data class AmbiguousFunctions(val functions: List<FunctionCallResolver.FunctionResolutionAndBinding>) : ErrorReason
    data class ValReassignment(val localVal: LocalValue) : ErrorReason
    data class ExternalReassignment(val external: ObjectOrigin.External) : ErrorReason
    data class AssignmentTypeMismatch(val expected: DataType, val actual: DataType) : ErrorReason

    data object UnusedConfigureLambda : ErrorReason
    data class DuplicateLocalValue(val name: String) : ErrorReason
    data object UnresolvedAssignmentLhs : ErrorReason // TODO: report candidate with rejection reasons
    data object UnresolvedAssignmentRhs : ErrorReason // TODO: resolution trace here, too?
    data object UnitAssignment : ErrorReason
    data object ReadOnlyPropertyAssignment : ErrorReason
    data object DanglingPureExpression : ErrorReason
}