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

class HtmlToXmlJavadocLexerTest extends Specification {
    def "discards whitespace around block elements"() {
        expect:
        parse(source) == transformed

        where:
        source                                             | transformed
        "  <p>text</p>   "                                 | "<p>text</p>"
        "<p>text</p>   <p>text</p>"                        | "<p>text</p><p>text</p>"
        "<p>text</p>   <h2>text</h2> <table/>"             | "<p>text</p><h2>text</h2><table></table>"
        "  <table>  <tr>  <td>text</td> </tr>\r\n</table>" | "<table><tr><td>text</td></tr></table>"
    }

    def "does not discard whitespace around inline elements"() {
        expect:
        parse(source) == transformed

        where:
        source                                             | transformed
        "  <code>code</code>  "                            | "<p><code>code</code></p>"
        "<p>  <code>code</code> \ttext\t\t<em>em</em></p>" | "<p>  <code>code</code> \ttext\t\t<em>em</em></p>"
        "<h1>text</h1> text  \t"                           | "<h1>text</h1><p> text</p>"
        "<ul><li>  text  <li> text  "                      | "<ul><li>  text  </li><li> text</li></ul>"
    }

    def "wraps text in an implicit <p> element"() {
        expect:
        parse(source) == transformed

        where:
        source                          | transformed
        "text"                          | "<p>text</p>"
        "   "                           | ""
        "   text "                      | "<p>text</p>"
        "text <code>inline</code> text" | "<p>text <code>inline</code> text</p>"
        "<ul>text</ul>"                 | "<ul><p>text</p></ul>"
    }

    def "wraps inline elements in an implicit <p> element"() {
        expect:
        parse(source) == transformed

        where:
        source                            | transformed
        "<em>text</em>"                   | "<p><em>text</em></p>"
        "<em>text</em> <code>text</code>" | "<p><em>text</em> <code>text</code></p>"
        "<ul><em>text</em></ul>"          | "<ul><p><em>text</em></p></ul>"
    }

    def "closes implicit <p> element at start of block element"() {
        expect:
        parse(source) == transformed

        where:
        source              | transformed
        "text<p>para 2</p>" | "<p>text</p><p>para 2</p>"
        "text<h2>text</h2>" | "<p>text</p><h2>text</h2>"
    }

    def "does not add implicit <p> element for elements with inline content"() {
        expect:
        parse(source) == transformed

        where:
        source                                   | transformed
        "<h1>text</h1>"                          | "<h1>text</h1>"
        "<h2>text</h2>"                          | "<h2>text</h2>"
        "<p>text</p>"                            | "<p>text</p>"
        "<h1><code>text</code></h1>"             | "<h1><code>text</code></h1>"
        "<table><th>text</th><td>text</td></ul>" | "<table><th>text</th><td>text</td></table>"
    }

    def "does not add implicit <p> element for anchor elements outside <p> elements"() {
        expect:
        parse(source) == transformed

        where:
        source                            | transformed
        "<a href='ref'>text</a>"          | "<p><a href='ref'>text</a></p>"
        "<a name='ref'/> text"            | "<a name='ref'></a><p> text</p>"
        "<a name='ref'/><h2>heading</h2>" | "<a name='ref'></a><h2>heading</h2>"
        "<p><a name='ref'/>"              | "<p><a name='ref'></a></p>"
        "<p><a name='ref'/>text"          | "<p><a name='ref'></a>text</p>"
        "<ul><a name='ref'/></ul>"        | "<ul><a name='ref'></a></ul>"
    }

    def "adds implicit end of element at end of input"() {
        expect:
        parse(source) == transformed

        where:
        source         | transformed
        "<p>text"      | "<p>text</p>"
        "<ul><li>text" | "<ul><li>text</li></ul>"
    }

    def "adds implicit end of element"() {
        expect:
        parse(source) == transformed

        where:
        source                                    | transformed
        "<p>text<p>text"                          | "<p>text</p><p>text</p>"
        "<ul><li>text<li>text"                    | "<ul><li>text</li><li>text</li></ul>"
        "<dl><dt>term<dd>item<dt>term<dt>term"    | "<dl><dt>term</dt><dd>item</dd><dt>term</dt><dt>term</dt></dl>"
        "<table><tr><th>cell<tr><td>cell<td>cell" | "<table><tr><th>cell</th></tr><tr><td>cell</td><td>cell</td></tr></table>"
        "<ul><li><ul><li>text<li>text"            | "<ul><li><ul><li>text</li><li>text</li></ul></li></ul>"
    }

    def "splits para on block content"() {
        expect:
        parse(source) == transformed

        where:
        source                      | transformed
        "text<ul><li>item</ul>text" | "<p>text</p><ul><li>item</li></ul><p>text</p>"
        "text<h2>header</h2>text"   | "<p>text</p><h2>header</h2><p>text</p>"
    }

    def parse(String source) {
        def lexer = new HtmlToXmlJavadocLexer(new BasicJavadocLexer(new JavadocScanner(source)))
        def result = new StringBuilder()
        lexer.visit(new JavadocLexer.TokenVisitor() {
            @Override
            void onStartHtmlElement(String name) {
                result.append("<$name")
            }

            @Override
            void onHtmlElementAttribute(String name, String value) {
                result.append(" $name='$value'")
            }

            @Override
            void onStartHtmlElementComplete(String name) {
                result.append(">")
            }

            @Override
            void onEndHtmlElement(String name) {
                result.append("</$name>")
            }

            @Override
            void onText(String text) {
                result.append(text)
            }
        })
        return result.toString()
    }
}
