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


class OutputTest extends Specification {
    final static String XML_TEXT = '<classpathentry kind="output" path="somePath"/>'

    def canReadFromXml() {
        when:
        Output output = new Output(new XmlParser().parseText(XML_TEXT))

        then:
        output == createOutput()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createOutput().appendNode(rootNode)

        then:
        new Output(rootNode.classpathentry[0]) == createOutput()
    }

    def equality() {
        Output output = createOutput()
        output.path += 'x'

        expect:
        output != createOutput()
    }

    private Output createOutput() {
        return new Output('somePath')
    }


}
