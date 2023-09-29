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


class ContainerTest extends Specification {
    final static String XML_TEXT = '''
                <classpathentry exported="true" kind="con" path="somePath">
                    <attributes>
                        <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                    </attributes>
                    <accessrules>
                        <accessrule kind="nonaccessible" pattern="secret**"/>
                    </accessrules>
                </classpathentry>'''

    def canReadFromXml() {
        when:
        Container container = new Container(new XmlParser().parseText(XML_TEXT))

        then:
        container == createContainer()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createContainer().appendNode(rootNode)

        then:
        new Container(rootNode.classpathentry[0]) == createContainer()
    }

    def equality() {
        Container container = createContainer()
        container.nativeLibraryLocation += 'x'

        expect:
        container != createContainer()
    }

    private Container createContainer() {
        Container container = new Container('somePath')
        container.exported = true
        container.nativeLibraryLocation = 'mynative'
        container.accessRules += [new AccessRule('nonaccessible', 'secret**')]
        return container
    }


}
