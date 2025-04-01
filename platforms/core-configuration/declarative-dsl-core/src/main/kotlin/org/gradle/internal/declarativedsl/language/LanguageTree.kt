package org.gradle.internal.declarativedsl.language

import org.gradle.declarative.dsl.schema.DataType


sealed interface LanguageTreeElement {
    val sourceData: SourceData
}


sealed interface FunctionArgument : LanguageTreeElement {
    sealed interface ValueLikeArgument : FunctionArgument

    sealed interface SingleValueArgument : ValueLikeArgument {
        val expr: Expr
    }

    data class Positional(override val expr: Expr, override val sourceData: SourceData) : SingleValueArgument {
        override fun toString() = "$expr"
    }
    data class Named(val name: String, override val expr: Expr, override val sourceData: SourceData) : SingleValueArgument {
        override fun toString() = "$name = $expr"
    }
    data class GroupedVarargs(val elementArgs: List<SingleValueArgument>): ValueLikeArgument {
        override val sourceData: SourceData
            get() = elementArgs.firstOrNull()?.sourceData ?: SyntheticallyProduced

        override fun toString(): String = "[${elementArgs.joinToString()}]"
    }
    data class Lambda(val block: Block, override val sourceData: SourceData) : FunctionArgument {
        override fun toString() = "{ ... }"
    }
}


sealed interface BlockElement : LanguageTreeElement


sealed interface DataStatement : LanguageTreeElement, BlockElement
sealed interface AssignmentLikeStatement : DataStatement {
    val lhs: NamedReference
    val rhs: Expr
}


data class ErroneousStatement(val failingResult: FailingResult) : BlockElement {
    override val sourceData: SourceData
        get() = error("use failing result for source data")
}


sealed interface Expr : DataStatement


data class Block(
    val content: List<BlockElement>,
    override val sourceData: SourceData
) : LanguageTreeElement {
    val statements: List<DataStatement>
        get() = content.filterIsInstance<DataStatement>()
}


data class Import(val name: AccessChain, override val sourceData: SourceData) : LanguageTreeElement


data class AccessChain(val nameParts: List<String>)


data class NamedReference(val receiver: Expr?, val name: String, override val sourceData: SourceData) : Expr {
    override fun toString() = "${receiver?.let { "$it." }.orEmpty()}$name"
}


data class FunctionCall(val receiver: Expr?, val name: String, val args: List<FunctionArgument>, override val sourceData: SourceData) : Expr {
    override fun toString() = "$name(${args.joinToString()})"
}


data class Assignment(override val lhs: NamedReference, override val rhs: Expr, override val sourceData: SourceData) : AssignmentLikeStatement

data class AugmentingAssignment(override val lhs: NamedReference, override val rhs: Expr, val augmentationKind: AugmentationOperatorKind, override val sourceData: SourceData) : AssignmentLikeStatement

sealed interface AugmentationOperatorKind {
    val operatorToken: String

    data object PlusAssign : AugmentationOperatorKind {
        override val operatorToken: String = "+="
    }
}

data class LocalValue(val name: String, val rhs: Expr, override val sourceData: SourceData) : DataStatement


sealed interface Literal<T : Any> : Expr {
    val value: T
    val type: DataType.ConstantType<T>

    data class StringLiteral(
        override val value: String,
        override val sourceData: SourceData
    ) : Literal<String> {
        override val type: DataType.StringDataType
            get() = DataTypeInternal.DefaultStringDataType

        override fun toString() = "\"$value\""
    }

    data class IntLiteral(
        override val value: Int,
        override val sourceData: SourceData
    ) : Literal<Int> {
        override val type: DataType.IntDataType
            get() = DataTypeInternal.DefaultIntDataType
        override fun toString() = value.toString()
    }

    data class LongLiteral(
        override val value: Long,
        override val sourceData: SourceData
    ) : Literal<Long> {
        override val type: DataType.LongDataType
            get() = DataTypeInternal.DefaultLongDataType
        override fun toString() = value.toString()
    }

    data class BooleanLiteral(
        override val value: Boolean,
        override val sourceData: SourceData
    ) : Literal<Boolean> {
        override val type: DataType.BooleanDataType
            get() = DataTypeInternal.DefaultBooleanDataType
        override fun toString() = value.toString()
    }
}


data class Null(override val sourceData: SourceData) : Expr {
    override fun toString() = "null"
}


data class This(override val sourceData: SourceData) : Expr {
    override fun toString() = "this"
}
