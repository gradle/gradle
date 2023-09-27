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
package org.gradle.plugins.ide.eclipse.model

import groovy.xml.XmlParser
import spock.lang.Specification


class WbPropertyTest extends Specification {
    final static String XML_TEXT = '<property name="java-output-path" value="/build/classes"/>'

    def canReadFromXml() {
        when:
        WbProperty wbProperty = new WbProperty(new XmlParser().parseText(XML_TEXT))

        then:
        wbProperty == createWbProperty()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createWbProperty().appendNode(rootNode)

        then:
        new WbProperty(rootNode.property[0]) == createWbProperty()
    }

    def equality() {
        WbProperty wbProperty = createWbProperty()
        wbProperty.name += 'x'

        expect:
        wbProperty != createWbProperty()
    }

    private WbProperty createWbProperty() {
        return new WbProperty("java-output-path", "/build/classes")
    }


}
