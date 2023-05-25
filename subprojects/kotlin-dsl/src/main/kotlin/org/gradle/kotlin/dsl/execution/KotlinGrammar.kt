/*
 * Copyright 2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.kotlin.dsl.execution

import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.lexer.KtTokens.ANDAND
import org.jetbrains.kotlin.lexer.KtTokens.ARROW
import org.jetbrains.kotlin.lexer.KtTokens.AT
import org.jetbrains.kotlin.lexer.KtTokens.CLASS_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.COLON
import org.jetbrains.kotlin.lexer.KtTokens.COLONCOLON
import org.jetbrains.kotlin.lexer.KtTokens.COMMA
import org.jetbrains.kotlin.lexer.KtTokens.DIV
import org.jetbrains.kotlin.lexer.KtTokens.DIVEQ
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.ELVIS
import org.jetbrains.kotlin.lexer.KtTokens.EQ
import org.jetbrains.kotlin.lexer.KtTokens.EQEQ
import org.jetbrains.kotlin.lexer.KtTokens.EQEQEQ
import org.jetbrains.kotlin.lexer.KtTokens.EXCL
import org.jetbrains.kotlin.lexer.KtTokens.EXCLEQ
import org.jetbrains.kotlin.lexer.KtTokens.EXCLEQEQEQ
import org.jetbrains.kotlin.lexer.KtTokens.GT
import org.jetbrains.kotlin.lexer.KtTokens.GTEQ
import org.jetbrains.kotlin.lexer.KtTokens.IN_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.IS_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.LBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.LT
import org.jetbrains.kotlin.lexer.KtTokens.LTEQ
import org.jetbrains.kotlin.lexer.KtTokens.MINUS
import org.jetbrains.kotlin.lexer.KtTokens.MINUSEQ
import org.jetbrains.kotlin.lexer.KtTokens.MINUSMINUS
import org.jetbrains.kotlin.lexer.KtTokens.MUL
import org.jetbrains.kotlin.lexer.KtTokens.MULTEQ
import org.jetbrains.kotlin.lexer.KtTokens.NOT_IN
import org.jetbrains.kotlin.lexer.KtTokens.NOT_IS
import org.jetbrains.kotlin.lexer.KtTokens.NULL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OROR
import org.jetbrains.kotlin.lexer.KtTokens.OUT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PERC
import org.jetbrains.kotlin.lexer.KtTokens.PERCEQ
import org.jetbrains.kotlin.lexer.KtTokens.PLUS
import org.jetbrains.kotlin.lexer.KtTokens.PLUSEQ
import org.jetbrains.kotlin.lexer.KtTokens.PLUSPLUS
import org.jetbrains.kotlin.lexer.KtTokens.QUEST
import org.jetbrains.kotlin.lexer.KtTokens.RANGE
import org.jetbrains.kotlin.lexer.KtTokens.RBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.SAFE_ACCESS
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON


internal
val kotlinGrammar: KotlinGrammar by lazy(LazyThreadSafetyMode.PUBLICATION) {
    KotlinGrammar()
}


internal
class KotlinGrammar : Combinator(true, true) {

    // todo: re-check all the definitions

    @Suppress("unused")
    private
    val debugger: ParserDebugger = ParserDebugger()

    @Suppress("UNUSED_PARAMETER")
    private
    fun <T> debug(name: String, parser: Parser<T>) =
//         debugger.debug(name, parser)
        parser

    // basic building blocks
    internal
    val simpleIdentifier =
        debug(
            "simpleIdentifier",
            symbol()
        )

    internal
    val label =
        simpleIdentifier * token(AT)

    internal
    val asOperator =
        token(KtTokens.AS_KEYWORD) + token(KtTokens.AS_SAFE)

    internal
    val additiveOperator =
        token(PLUS) + token(MINUS)

    internal
    val varianceModifier =
        token(IN_KEYWORD) + token(OUT_KEYWORD)

    internal
    val inOperator =
        token(IN_KEYWORD) + token(NOT_IN)

    internal
    val isOperator =
        token(IS_KEYWORD) + token(NOT_IS)

    internal
    val comparisonOperator =
        token(LT) + token(GT) + token(LTEQ) + token(GTEQ)

    internal
    val equalityOperator =
        token(EXCLEQ) + token(EXCLEQEQEQ) + token(EQEQ) + token(EQEQEQ)

    internal
    val prefixUnaryOperator =
        token(PLUSPLUS) + token(MINUSMINUS) + token(MINUS) + token(PLUS) + token(EXCL)

    internal
    val multiplicativeOperator =
        token(MUL) + token(DIV) + token(PERC)

    internal
    val memberAccessOperator =
        token(DOT) + token(SAFE_ACCESS) + token(COLONCOLON)

    internal
    val semi =
        token(SEMICOLON)

    internal
    val semis =
        oneOrMore(semi)

    internal
    val assignmentAndOperator =
        token(PLUSEQ) + token(MINUSEQ) + token(MULTEQ) + token(DIVEQ) + token(PERCEQ)


    internal
    val literalConstant =
        booleanLiteral + integerLiteral + floatLiteral + characterLiteral + token(NULL_KEYWORD)


    // recursive parsers
    internal
    var type by reference<Any>()

    internal
    var annotation by reference<Any>()

    internal
    var fileAnnotation by reference<Any>()

    internal
    var parenthesizedUserType by reference<Any>()

    internal
    var expression by reference<Any>()

    internal
    var primaryExpression by reference<Any?>()

    internal
    var directlyAssignableExpression by reference<Any>()

    internal
    var assignableExpression by reference<Any>()


    // regular parsers
    internal
    val collectionLiteral =
        bracket(
            optional(
                expression * zeroOrMore(token(COMMA) * expression) * optional(token(COMMA))
            )
        )

    internal
    val parenthesizedType = paren(type)

    internal
    val parenthesizedExpression =
        paren(expression)

    internal
    val valueArgument =
        debug(
            "valueArgument",
            optional(annotation) *
                debug(
                    "optional(simpleIdentifier * token(EQ))",
                    optional(simpleIdentifier * token(EQ))
                ) *
                optional(token(MUL)) *
                expression
        )

    internal
    val valueArguments =
        debug(
            "valueArguments",
            paren(
                optional(
                    valueArgument * zeroOrMore(token(COMMA) * valueArgument) * optional(token(COMMA))
                )
            )
        )

    internal
    val variableDeclaration =
        zeroOrMore(annotation) *
            simpleIdentifier *
            optional(token(COMMA) * type)

    internal
    val multiVariableDeclaration =
        paren(
            variableDeclaration *
                zeroOrMore(token(COMMA) * variableDeclaration) *
                optional(token(COMMA))
        )

    internal
    val typeModifier =
        annotation + symbol("suspend") + token(KtTokens.SUSPEND_KEYWORD)

    internal
    val typeModifiers =
        oneOrMore(typeModifier)

    internal
    val typeProjectionModifier =
        varianceModifier + annotation

    internal
    val typeProjectionModifiers =
        oneOrMore(typeProjectionModifier)

    internal
    val typeProjection =
        optional(typeProjectionModifiers) * type + token(MUL)

    internal
    val typeArguments =
        token(LT) *
            typeProjection *
            zeroOrMore(token(COMMA) * typeProjection) *
            optional(token(COMMA)) *
            token(GT)

    internal
    val simpleUserType =
        debug(
            "simpleUserType",
            simpleIdentifier * optional(typeArguments)
        )

    internal
    val userType =
        debug(
            "userType",
            simpleUserType * zeroOrMore(token(DOT) * simpleUserType)
        )

    internal
    val unaryPrefix =
        debug(
            "unaryPrefix",
            annotation + label + prefixUnaryOperator
        )

    internal
    val lambdaParameter =
        variableDeclaration +
            (multiVariableDeclaration * optional(token(COLON) * type))

    internal
    val lambdaParameters =
        lambdaParameter *
            zeroOrMore(token(COMMA) * lambdaParameter) *
            optional(token(COMMA))

    internal
    val assignment =
        debug(
            "assignment",
            ((directlyAssignableExpression * token(EQ)) + (assignableExpression * assignmentAndOperator)) * expression
        )

    internal
    val statement =
        debug(
            "statement",
            zeroOrMore(label + annotation) *
                (assignment + expression)
        )

    internal
    val statements: Parser<Any> =
        debug(
            "statements",
            optional(statement * zeroOrMore(semis * statement)) * optional(semis)
        )

    internal
    val lambdaLiteral =
        brace(
            (optional(lambdaParameters) *
                optional(token(ARROW))) *
                statements
        )

    internal
    val annotatedLambda =
        zeroOrMore(annotation) *
            optional(label) *
            lambdaLiteral

    internal
    val callSuffix =
        optional(typeArguments) *
            ((optional(valueArguments) * annotatedLambda) + valueArguments)

    internal
    val indexingSuffix =
        bracket(
            expression *
                zeroOrMore(token(COMMA) * expression * optional(token(COMMA)))
        )

    internal
    val navigationSuffix: Parser<Any> =
        debug(
            "navigationSuffix",
            memberAccessOperator * (simpleIdentifier + parenthesizedExpression + token(CLASS_KEYWORD))
        )

    internal
    val postfixUnarySuffix =
        typeArguments + callSuffix + indexingSuffix + navigationSuffix

    internal
    val postfixUnaryExpression =
        debug(
            "postfixUnaryExpression",
            primaryExpression *
                debug(
                    "zeroOrMore(postfixUnarySuffix)",
                    zeroOrMore(postfixUnarySuffix)
                )
        )

    internal
    val prefixUnaryExpression =
        debug(
            "prefixUnaryExpression",
            zeroOrMore(unaryPrefix) *
                postfixUnaryExpression
        )

    internal
    val asExpression =
        debug(
            "asExpression",
            prefixUnaryExpression *
                zeroOrMore(asOperator * type)
        )

    internal
    val multiplicativeExpression =
        debug(
            "multiplicativeExpression",
            asExpression *
                zeroOrMore(multiplicativeOperator * asExpression)
        )

    internal
    val additiveExpression =
        debug(
            "additiveExpression",
            multiplicativeExpression * zeroOrMore(additiveOperator * multiplicativeExpression)
        )

    internal
    val rangeExpression =
        debug(
            "rangeExpression",
            additiveExpression * zeroOrMore(token(RANGE) + additiveExpression)
        )

    internal
    val infixFunctionCall =
        debug(
            "infixFunctionCall",
            rangeExpression * zeroOrMore(simpleIdentifier * rangeExpression)
        )

    internal
    val elvisExpression =
        debug(
            "elvisExpression",
            infixFunctionCall * zeroOrMore(token(ELVIS) * infixFunctionCall)
        )

    internal
    val infixOperation =
        debug(
            "infixOperation",
            elvisExpression * zeroOrMore((inOperator * elvisExpression) + (isOperator * type))
        )

    internal
    val genericCallLikeComparison =
        debug(
            "genericCallLikeComparison",
            infixOperation * zeroOrMore(callSuffix)
        )

    internal
    val comparison =
        debug(
            "comparison",
            genericCallLikeComparison * zeroOrMore(comparisonOperator * genericCallLikeComparison)
        )

    internal
    val equality =
        debug(
            "equality",
            comparison * zeroOrMore(equalityOperator * comparison)
        )

    internal
    val conjunction =
        debug(
            "conjunction",
            equality * zeroOrMore(token(ANDAND) * equality)
        )

    internal
    val disjunction =
        conjunction * zeroOrMore(token(OROR) * conjunction)


    internal
    val definitelyNonNullableType =
        optional(typeModifiers) *
            (userType + parenthesizedUserType) *
            token(MUL) *
            optional(typeModifiers) *
            (userType + parenthesizedUserType)

    internal
    val constructorInvocation =
        debug(
            "constructorInvocation",
            userType * valueArguments
        )

    internal
    val unescapedAnnotation =
        debug("unescapedAnnotation",
            constructorInvocation + userType
        )

    internal
    val listOfUnescapedAnnotations =
        debug(
            "listOfUnescapedAnnotations",
            token(LBRACKET) * oneOrMore(unescapedAnnotation) * token(RBRACKET)
        )

    internal
    val annotationUseSiteTarget =
        debug(
            "annotationUseSiteTarget",
            (symbol("field") + symbol("property") + symbol("get") + symbol("set") +
                symbol("receiver") + symbol("param") + symbol("setparam") + symbol("delgate")) * token(COLON)
        )

    // todo: experiment with weird stuff, try to drop things like lambda & statement

    internal
    val singleAnnotation =
        debug(
            "singleAnnotation",
            token(AT) * optional(annotationUseSiteTarget) * unescapedAnnotation
        )

    internal
    val multiAnnotation = token(AT) * optional(annotationUseSiteTarget) * listOfUnescapedAnnotations

    internal
    val parameter =
        simpleIdentifier * token(COLON) * type

    internal
    val functionTypeParameters =
        paren(
            optional(parameter + type) *
                zeroOrMore(token(COMMA) * (parameter + type)) *
                optional(token(COMMA))
        )

    internal
    val typeReference =
        userType + symbol("dynamic")


    internal
    val nullableType =
        (typeReference + parenthesizedType) * oneOrMore(token(QUEST))

    internal
    val receiverType =
        debug(
            "receiverType",
            optional(typeModifiers) * (parenthesizedType + nullableType + typeReference)
        )

    internal
    val functionType =
        optional(receiverType * token(DOT)) * functionTypeParameters * token(ARROW) * type

    internal
    val callableReference =
        debug(
            "callableReference",
            optional(receiverType) *
                debug(
                    "token(COLONCOLON)",
                    token(COLONCOLON)
                ) *
                debug(
                    "(simpleIdentifier + token(CLASS_KEYWORD))",
                    (simpleIdentifier + token(CLASS_KEYWORD))
                )
        )

    internal
    val assignableSuffix =
        debug(
            "assignableSuffix",
            typeArguments + indexingSuffix + navigationSuffix
        )


    init {
        type = optional(typeModifiers) * (functionType + parenthesizedType + nullableType + typeReference + definitelyNonNullableType)
        annotation = singleAnnotation + multiAnnotation
        fileAnnotation = token(AT) * symbol("file") * token(COLON) * (unescapedAnnotation + listOfUnescapedAnnotations)
        parenthesizedUserType = paren(userType + parenthesizedUserType)
        expression =
            debug(
                "expression",
                disjunction
            )
        primaryExpression =
            debug("primaryExpression",
                parenthesizedExpression +
                    callableReference +
                    simpleIdentifier +
                    literalConstant +
                    stringLiteral +
                    collectionLiteral
            )
        directlyAssignableExpression =
            debug(
                "directlyAssignableExpression",
                (postfixUnaryExpression * assignableSuffix) +
                    simpleIdentifier +
                    paren(directlyAssignableExpression)
            )
        assignableExpression =
            debug(
                "assignableExpression",
                prefixUnaryExpression + paren(assignableExpression)
            )
    }
}
