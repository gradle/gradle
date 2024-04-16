package org.gradle.internal.declarativedsl.language


sealed interface LanguageTreeElement {
    val sourceData: SourceData
}


sealed interface FunctionArgument : LanguageTreeElement {
    sealed interface ValueArgument : FunctionArgument {
        val expr: Expr
    }

    data class Positional(override val expr: Expr, override val sourceData: SourceData) : ValueArgument
    data class Named(val name: String, override val expr: Expr, override val sourceData: SourceData) : ValueArgument
    data class Lambda(val block: Block, override val sourceData: SourceData) : FunctionArgument
}


sealed interface BlockElement : LanguageTreeElement


sealed interface DataStatement : LanguageTreeElement, BlockElement


data class ErroneousStatement(val failingResult: FailingResult) : BlockElement {
    override val sourceData: SourceData
        get() = error("Use failing result for source data")
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


data class PropertyAccess(val receiver: Expr?, val name: String, override val sourceData: SourceData) : Expr


data class FunctionCall(val receiver: Expr?, val name: String, val args: List<FunctionArgument>, override val sourceData: SourceData) : Expr


data class Assignment(val lhs: PropertyAccess, val rhs: Expr, override val sourceData: SourceData) : DataStatement


data class LocalValue(val name: String, val rhs: Expr, override val sourceData: SourceData) : DataStatement


sealed interface Literal<T : Any> : Expr {
    val value: T
    val type: DataTypeInternal.ConstantType<T>

    data class StringLiteral(
        override val value: String,
        override val sourceData: SourceData
    ) : Literal<String> {
        override val type: DataTypeInternal.StringType
            get() = DataTypeInternal.StringType
    }

    data class IntLiteral(
        override val value: Int,
        override val sourceData: SourceData
    ) : Literal<Int> {
        override val type: DataTypeInternal.IntType
            get() = DataTypeInternal.IntType
    }

    data class LongLiteral(
        override val value: Long,
        override val sourceData: SourceData
    ) : Literal<Long> {
        override val type: DataTypeInternal.LongType
            get() = DataTypeInternal.LongType
    }

    data class BooleanLiteral(
        override val value: Boolean,
        override val sourceData: SourceData
    ) : Literal<Boolean> {
        override val type: DataTypeInternal.BooleanType
            get() = DataTypeInternal.BooleanType
    }
}


data class Null(override val sourceData: SourceData) : Expr


data class This(override val sourceData: SourceData) : Expr
