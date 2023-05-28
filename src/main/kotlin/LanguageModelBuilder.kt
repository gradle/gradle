package com.h0tk3y.kotlin.staticObjectNotation

import kotlinx.ast.common.ast.Ast

data class LanguageTreeResult(
    val elements: List<LanguageTreeElement>,
    val failures: List<LanguageModelBuildingFailure>
)

sealed interface LanguageModelBuildingFailure {
    val potentialElementAst: Ast
    val erroneousAst: Ast

    data class FunctionCallInLhs(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast
    ) : LanguageModelBuildingFailure
    
    data class NameOutOfSchema(
        override val potentialElementAst: Ast,
        override val erroneousAst: Ast,
    ) : LanguageModelBuildingFailure
    
    data class TypeDeclaration(
        val typeAst: Ast
    ) : LanguageModelBuildingFailure{
        override val potentialElementAst: Ast get() = typeAst
        override val erroneousAst: Ast get() = typeAst
    }
    
    data class StarImport(
        val importAst: Ast
    ) : LanguageModelBuildingFailure {
        override val potentialElementAst: Ast get() = importAst 
        override val erroneousAst: Ast get() = importAst
    }
    
    data class TodoProvideDetails(
        val ast: Ast
    ) : LanguageModelBuildingFailure {
        override val potentialElementAst: Ast get() = ast 
        override val erroneousAst: Ast = ast

    }
}

class LanguageModelBuilder {
}
