/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.docbook

import spock.lang.Specification

class BasicJavadocLexerTest extends Specification {
    final BasicJavadocLexer lexer = new BasicJavadocLexer(new JavadocScanner(""))
    final visitor = Mock(JavadocLexer.TokenVisitor)

    def "parses html elements"() {
        when:
        lexer.pushText("<p> text </p>")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('p')
        1 * visitor.onStartHtmlElementComplete('p')
        1 * visitor.onText(' text ')
        1 * visitor.onEndHtmlElement('p')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "normalizes html element names to lowercase"() {
        when:
        lexer.pushText("<P></End>")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('p')
        1 * visitor.onStartHtmlElementComplete('p')
        1 * visitor.onEndHtmlElement('end')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "parses html entities"() {
        when:
        lexer.pushText("before &amp; after")
        lexer.visit(visitor)

        then:
        1 * visitor.onText('before & after')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "parses start html element with attributes"() {
        when:
        lexer.pushText("<a name='value' other='\n&amp; &apos;\n \"'>")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('a')
        1 * visitor.onHtmlElementAttribute('name', 'value')
        1 * visitor.onHtmlElementAttribute('other', '\n& \'\n \"')
        1 * visitor.onStartHtmlElementComplete('a')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "can use single or double quotes for attribute values"() {
        when:
        lexer.pushText("<a single='a=\"b\"' double = \"a='b'\">")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('a')
        1 * visitor.onHtmlElementAttribute('single', 'a=\"b\"')
        1 * visitor.onHtmlElementAttribute('double', 'a=\'b\'')
        1 * visitor.onStartHtmlElementComplete('a')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "splits html element with no content into separate start and end tokens"() {
        when:
        lexer.pushText("<p/>")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('p')
        1 * visitor.onStartHtmlElementComplete('p')
        1 * visitor.onEndHtmlElement('p')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "discards html comments"() {
        when:
        lexer.pushText("<p><!-- ignore me --></p>text <!-- -->2")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('p')
        1 * visitor.onStartHtmlElementComplete('p')
        1 * visitor.onEndHtmlElement('p')
        1 * visitor.onText("text 2")
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "handles missing end of comment"() {
        when:
        lexer.pushText("<p><!-- ignore me ")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartHtmlElement('p')
        1 * visitor.onStartHtmlElementComplete('p')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "parses javadoc tags"() {
        when:
        lexer.pushText("{@tag some value}")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartJavadocTag('tag')
        1 * visitor.onText('some value')
        1 * visitor.onEndJavadocTag('tag')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "javadoc tag can be empty"() {
        when:
        lexer.pushText("{@empty}")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartJavadocTag('empty')
        1 * visitor.onEndJavadocTag('empty')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "ignores whitespace and EOL chars between javadoc tag name and value"() {
        when:
        lexer.pushText("* {@link\n *  Something}")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartJavadocTag('link')
        1 * visitor.onText('Something')
        1 * visitor.onEndJavadocTag('link')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "ignores badly formed html element"() {
        when:
        lexer.pushText("a << b")
        lexer.visit(visitor)

        then:
        1 * visitor.onText('a << b')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "javadoc tag can contain EOL chars"() {
        when:
        lexer.pushText(" * {@link #Something(Object,\n * String\n * }")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartJavadocTag('link')
        1 * visitor.onText('#Something(Object,\nString\n')
        1 * visitor.onEndJavadocTag('link')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "does not parse html elements inside javadoc tag"() {
        when:
        lexer.pushText("{@link <something> & &lt; </something>}")
        lexer.visit(visitor)

        then:
        1 * visitor.onStartJavadocTag('link')
        1 * visitor.onText('<something> & &lt; </something>')
        1 * visitor.onEndJavadocTag('link')
        1 * visitor.onEnd()
        0 * visitor._
    }

    def "javadoc tag cannot have whitespace inside marker"() {
        when:
        lexer.pushText("{ @code} {@ code} { @ code}")
        lexer.visit(visitor)

        then:
        1 * visitor.onText('{ @code} ')
        1 * visitor.onStartJavadocTag('')
        1 * visitor.onText('code')
        1 * visitor.onEndJavadocTag('')
        1 * visitor.onText(' { @ code}')
        1 * visitor.onEnd()
        0 * visitor._
    }
}
