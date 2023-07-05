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

import org.jetbrains.kotlin.lexer.KtTokens.ANDAND
import org.jetbrains.kotlin.lexer.KtTokens.ARROW
import org.jetbrains.kotlin.lexer.KtTokens.AT
import org.jetbrains.kotlin.lexer.KtTokens.CLASS_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.COLON
import org.jetbrains.kotlin.lexer.KtTokens.COLONCOLON
import org.jetbrains.kotlin.lexer.KtTokens.COMMA
import org.jetbrains.kotlin.lexer.KtTokens.DOT
import org.jetbrains.kotlin.lexer.KtTokens.EQ
import org.jetbrains.kotlin.lexer.KtTokens.EQEQ
import org.jetbrains.kotlin.lexer.KtTokens.EQEQEQ
import org.jetbrains.kotlin.lexer.KtTokens.EXCLEQ
import org.jetbrains.kotlin.lexer.KtTokens.EXCLEQEQEQ
import org.jetbrains.kotlin.lexer.KtTokens.GT
import org.jetbrains.kotlin.lexer.KtTokens.GTEQ
import org.jetbrains.kotlin.lexer.KtTokens.LBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.LT
import org.jetbrains.kotlin.lexer.KtTokens.LTEQ
import org.jetbrains.kotlin.lexer.KtTokens.MUL
import org.jetbrains.kotlin.lexer.KtTokens.NULL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.OROR
import org.jetbrains.kotlin.lexer.KtTokens.QUEST
import org.jetbrains.kotlin.lexer.KtTokens.RBRACKET
import org.jetbrains.kotlin.lexer.KtTokens.SAFE_ACCESS
import org.jetbrains.kotlin.lexer.KtTokens.SEMICOLON


internal
val kotlinGrammar: KotlinGrammar by lazy(LazyThreadSafetyMode.PUBLICATION) {
    KotlinGrammar()
}


@Suppress("MemberVisibilityCanBePrivate")
internal
class KotlinGrammar : Combinator(true, true) {

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
    val comparisonOperator =
        token(LT) + token(GT) + token(LTEQ) + token(GTEQ)

    internal
    val equalityOperator =
        token(EXCLEQ) + token(EXCLEQEQEQ) + token(EQEQ) + token(EQEQEQ)

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
    val userType =
        debug(
            "userType",
            simpleIdentifier * zeroOrMore(token(DOT) * simpleIdentifier)
        )

    internal
    val unaryPrefix =
        debug(
            "unaryPrefix",
            annotation + label
        )

    internal
    val assignment =
        debug(
            "assignment",
            directlyAssignableExpression * token(EQ) * expression
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
        valueArguments + indexingSuffix + navigationSuffix

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
    val infixFunctionCall =
        debug(
            "infixFunctionCall",
            prefixUnaryExpression * zeroOrMore(simpleIdentifier * prefixUnaryExpression)
        )

    internal
    val genericCallLikeComparison =
        debug(
            "genericCallLikeComparison",
            infixFunctionCall * zeroOrMore(valueArguments)
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
        (userType + parenthesizedUserType) * token(MUL) * (userType + parenthesizedUserType)

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
            optional(parameter + type) * zeroOrMore(token(COMMA) * (parameter + type)) * optional(token(COMMA))
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
            parenthesizedType + nullableType + typeReference
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
            indexingSuffix + navigationSuffix
        )


    init {
        type = functionType + parenthesizedType + nullableType + typeReference + definitelyNonNullableType
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
