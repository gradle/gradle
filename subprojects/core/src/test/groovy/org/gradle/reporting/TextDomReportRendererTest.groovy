/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.reporting

import spock.lang.Specification
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Text

class TextDomReportRendererTest extends Specification {
    final TextReportRenderer<String> textRenderer = new TextReportRenderer<String>() {
        @Override protected void writeTo(String model, Writer out) {
            out.write("<html><p>$model</p></html>")
        }
    }
    final TextDomReportRenderer<String> renderer = new TextDomReportRenderer<String>(textRenderer)

    def "converts text to DOM elements"() {
        def doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        def parent = doc.createElement("parent")

        when:
        renderer.render("test", parent)

        then:
        parent.childNodes.length == 1
        parent.childNodes.item(0) instanceof Element
        parent.childNodes.item(0).nodeName == 'p'
        parent.childNodes.item(0).childNodes.length == 1
        parent.childNodes.item(0).childNodes.item(0) instanceof Text
        parent.childNodes.item(0).childNodes.item(0).nodeValue == "test"
    }
}
