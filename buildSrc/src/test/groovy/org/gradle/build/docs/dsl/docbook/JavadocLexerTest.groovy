/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.docbook

import spock.lang.Specification

class JavadocLexerTest extends Specification {
    final BasicJavadocLexer lexer = new BasicJavadocLexer(new JavadocScanner(""))

    def ignoresWhitespaceAndEOLCharsInsideTag() {
        when:
        lexer.pushText("{@link\n  Something  \n}")

        then:
        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.StartTag
        lexer.token.value == 'link'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.Text
        lexer.token.value == 'Something  \n'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.End
        lexer.token.value == 'link'

        !lexer.next()
    }

    def doesNotParseHtmlElementsInsideTag() {
        when:
        lexer.pushText("{@link <something> & &lt; </something>}")

        then:
        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.StartTag
        lexer.token.value == 'link'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.Text
        lexer.token.value == '<something> & &lt; </something>'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.End
        lexer.token.value == 'link'

        !lexer.next()
    }

    def tagCannotHaveWhitespaceInsideMarker() {
        when:
        lexer.pushText("{ @code} {@ code} { @ code}")

        then:
        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.Text
        lexer.token.value == '{ @code} '

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.StartTag
        lexer.token.value == ''

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.Text
        lexer.token.value == 'code'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.End
        lexer.token.value == ''

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.Text
        lexer.token.value == ' { @ code}'

        !lexer.next()
    }

    def splitsHtmlElementWithNoContentIntoStatAndEndTokens() {
        when:
        lexer.pushText("<p/>")

        then:
        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.StartElement
        lexer.token.value == 'p'

        lexer.next()
        lexer.token.tokenType == JavadocLexer.TokenType.End
        lexer.token.value == 'p'

        !lexer.next()
    }
}
