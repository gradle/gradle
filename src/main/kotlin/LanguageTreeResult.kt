package com.h0tk3y.kotlin.staticObjectNotation

import kotlinx.ast.common.ast.Ast

data class LanguageTreeResult(
    val results: List<ElementOrFailureResult<*>>
)

sealed interface ElementOrFailureResult<out T : LanguageTreeElement> {
    sealed interface FailingResult : ElementOrFailureResult<Nothing>

    data class ElementResult<T : LanguageTreeElement>(val element: T) : ElementOrFailureResult<T>
    data class UnsupportedConstruct(val failureResult: LanguageModelUnsupportedConstruct) : FailingResult
    data class MultipleFailuresResult(val failures: List<FailingResult>) : FailingResult
}

sealed interface LanguageModelUnsupportedConstruct {
    val potentialElementAst: Ast
    val erroneousAst: Ast

    data class PackageHeader(
        val packageHeaderAst: Ast
    ) : LanguageModelUnsupportedConstruct {
        override val potentialElementAst: Ast get() = packageHeaderAst
        override val erroneousAst: Ast get() = packageHeaderAst
    }

    data class FunctionCallInAccessChain(
        override val potentialElementAst: Ast, 
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class NameOutOfSchema(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast,
    ) : LanguageModelUnsupportedConstruct

    data class TypeDeclaration(
        val typeAst: Ast
    ) : LanguageModelUnsupportedConstruct {
        override val potentialElementAst: Ast get() = typeAst
        override val erroneousAst: Ast get() = typeAst
    }

    data class ExplicitVariableType(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class StarImport(
        val importAst: Ast
    ) : LanguageModelUnsupportedConstruct {
        override val potentialElementAst: Ast get() = importAst
        override val erroneousAst: Ast get() = importAst
    }

    data class RenamingImport(
        val importAst: Ast
    ) : LanguageModelUnsupportedConstruct {
        override val potentialElementAst: Ast get() = importAst
        override val erroneousAst: Ast get() = importAst
    }

    data class AnnotationNotSupported(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class UnsupportedLiteral(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class VarUnsupported(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class ValModifierUnsupported(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class IndexedAssignment(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelUnsupportedConstruct

    data class TodoNotCoveredYet(
        val ast: Ast
    ) : LanguageModelUnsupportedConstruct {
        override val potentialElementAst: Ast get() = ast
        override val erroneousAst: Ast = ast
    }
}

