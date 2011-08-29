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
import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

class TabsRendererTest extends Specification {
    final DomReportRenderer<String> contentRenderer = new DomReportRenderer<String>() {
        @Override
        void render(String model, Element parent) {
            parent.appendChild(parent.ownerDocument.createTextNode(model))
        }
    }
    final TabsRenderer renderer = new TabsRenderer()

    def "renders tabs"() {
        given:
        def doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        def parent = doc.createElement("parent")

        and:
        renderer.add('tab 1', contentRenderer)
        renderer.add('tab 2', contentRenderer)

        when:
        renderer.render("test", parent)

        then:
        parent.childNodes.length == 1
        parent.childNodes.item(0) instanceof Element
        parent.childNodes.item(0).nodeName == 'div'
    }
}
