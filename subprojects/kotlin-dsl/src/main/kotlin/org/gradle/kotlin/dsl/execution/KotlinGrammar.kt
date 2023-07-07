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

    // basic building blocks
    val simpleIdentifier by debug {
        symbol()
    }

    val label by debug {
        simpleIdentifier * token(AT)
    }

    val comparisonOperator by debug {
        token(LT) + token(GT) + token(LTEQ) + token(GTEQ)
    }

    val equalityOperator by debug {
        token(EXCLEQ) + token(EXCLEQEQEQ) + token(EQEQ) + token(EQEQEQ)
    }

    val memberAccessOperator by debug {
        token(DOT) + token(SAFE_ACCESS) + token(COLONCOLON)
    }

    val semi =
        token(SEMICOLON)

    val semis =
        oneOrMore(semi)


    val literalConstant =
        booleanLiteral + integerLiteral + floatLiteral + characterLiteral + token(NULL_KEYWORD)


    // recursive parsers
    var type by reference<Any>()

    var annotation by reference<Any>()

    var fileAnnotation by reference<Any>()

    var parenthesizedUserType by reference<Any>()

    var expression by reference<Any>()

    var primaryExpression by reference<Any?>()

    var directlyAssignableExpression by reference<Any>()

    var assignableExpression by reference<Any>()


    // regular parsers
    val collectionLiteral =
        bracket(
            optional(
                expression * zeroOrMore(token(COMMA) * expression) * optional(token(COMMA))
            )
        )

    val parenthesizedType by debug {
        paren(type)
    }

    val parenthesizedExpression by debug {
        paren(expression)
    }

    val valueArgument by debug {
        optional(annotation) * optional(simpleIdentifier * token(EQ)) * optional(token(MUL)) * expression
    }

    val valueArguments by debug {
        paren(
            optional(
                valueArgument * zeroOrMore(token(COMMA) * valueArgument) * optional(token(COMMA))
            )
        )
    }

    val userType by debug {
        simpleIdentifier * zeroOrMore(token(DOT) * simpleIdentifier)
    }

    val unaryPrefix by debug {
        annotation + label
    }

    val assignment by debug {
        directlyAssignableExpression * token(EQ) * expression
    }

    val statement by debug {
        zeroOrMore(label + annotation) * (assignment + expression)
    }

    val statements: Parser<Any> by debug {
        optional(statement * zeroOrMore(semis * statement)) * optional(semis)
    }

    val indexingSuffix by debug {
        bracket(
            expression * zeroOrMore(token(COMMA) * expression * optional(token(COMMA)))
        )
    }

    val navigationSuffix: Parser<Any> by debug {
        memberAccessOperator * (simpleIdentifier + parenthesizedExpression + token(CLASS_KEYWORD))
    }

    val postfixUnarySuffix =
        valueArguments + indexingSuffix + navigationSuffix

    val postfixUnaryExpression by debug {
        primaryExpression * zeroOrMore(postfixUnarySuffix)
    }

    val prefixUnaryExpression by debug {
        zeroOrMore(unaryPrefix) * postfixUnaryExpression
    }

    val infixFunctionCall by debug {
        prefixUnaryExpression * zeroOrMore(simpleIdentifier * prefixUnaryExpression)
    }

    val genericCallLikeComparison by debug {
        infixFunctionCall * zeroOrMore(valueArguments)
    }

    val comparison by debug {
        genericCallLikeComparison * zeroOrMore(comparisonOperator * genericCallLikeComparison)
    }

    val equality by debug {
        comparison * zeroOrMore(equalityOperator * comparison)
    }

    val conjunction by debug {
        equality * zeroOrMore(token(ANDAND) * equality)
    }

    val disjunction =
        conjunction * zeroOrMore(token(OROR) * conjunction)


    val definitelyNonNullableType =
        (userType + parenthesizedUserType) * token(MUL) * (userType + parenthesizedUserType)

    val constructorInvocation by debug {
        userType * valueArguments
    }

    val unescapedAnnotation by debug {
        constructorInvocation + userType
    }

    val listOfUnescapedAnnotations by debug {
        token(LBRACKET) * oneOrMore(unescapedAnnotation) * token(RBRACKET)
    }

    val annotationUseSiteTarget by debug {
        (symbol("field") + symbol("property") + symbol("get") + symbol("set") +
            symbol("receiver") + symbol("param") + symbol("setparam") + symbol("delgate")) * token(COLON)
    }

    val singleAnnotation by debug {
        token(AT) * optional(annotationUseSiteTarget) * unescapedAnnotation
    }

    val multiAnnotation by debug {
        token(AT) * optional(annotationUseSiteTarget) * listOfUnescapedAnnotations
    }

    val parameter by debug {
        simpleIdentifier * token(COLON) * type
    }

    val functionTypeParameters by debug {
        paren(
            optional(parameter + type) * zeroOrMore(token(COMMA) * (parameter + type)) * optional(token(COMMA))
        )
    }

    val typeReference =
        userType + symbol("dynamic")


    val nullableType =
        (typeReference + parenthesizedType) * oneOrMore(token(QUEST))

    val receiverType by debug {
        parenthesizedType + nullableType + typeReference
    }

    val functionType =
        optional(receiverType * token(DOT)) * functionTypeParameters * token(ARROW) * type

    val callableReference by debug {
        optional(receiverType) * token(COLONCOLON) * (simpleIdentifier + token(CLASS_KEYWORD))
    }

    val assignableSuffix by debug {
        indexingSuffix + navigationSuffix
    }


    init {
        type = functionType + parenthesizedType + nullableType + typeReference + definitelyNonNullableType
        annotation = singleAnnotation + multiAnnotation
        fileAnnotation = token(AT) * symbol("file") * token(COLON) * (unescapedAnnotation + listOfUnescapedAnnotations)
        parenthesizedUserType = paren(userType + parenthesizedUserType)

        expression = debugReference {
            disjunction
        }

        primaryExpression = debugReference {
            parenthesizedExpression + callableReference + simpleIdentifier + literalConstant + stringLiteral + collectionLiteral
        }

        directlyAssignableExpression = debugReference {
            (postfixUnaryExpression * assignableSuffix) + simpleIdentifier + paren(directlyAssignableExpression)
        }

        assignableExpression = debugReference {
            prefixUnaryExpression + paren(assignableExpression)
        }
    }
}
