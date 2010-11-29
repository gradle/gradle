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
    final JavadocLexer lexer = new JavadocLexer(new JavadocScanner(""))

    def ignoresWhitespaceAndEOLCharsInsideTag() {
        when:
        lexer.pushText("{@link\n  Something  \n}")

        then:
        lexer.next()
        lexer.token == JavadocLexer.Token.StartTag
        lexer.value == 'link'

        lexer.next()
        lexer.token = JavadocLexer.Token.Text
        lexer.value == 'Something  \n'

        lexer.next()
        lexer.token == JavadocLexer.Token.End
        lexer.value == 'link'
    }
    
    def splitsHtmlElementWithNoContentIntoStatAndEndTokens() {
        when:
        lexer.pushText("<p/>")

        then:
        lexer.next()
        lexer.token == JavadocLexer.Token.StartElement
        lexer.value == 'p'

        lexer.next()
        lexer.token == JavadocLexer.Token.End
        lexer.value == 'p'

        !lexer.next()
    }
}
