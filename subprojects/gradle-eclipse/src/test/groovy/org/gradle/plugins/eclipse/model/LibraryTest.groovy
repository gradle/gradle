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
package org.gradle.plugins.eclipse.model

import spock.lang.Specification

/**
 * @author Hans Dockter
 */

class LibraryTest extends Specification {
    final static String XML_TEXT = '''
                    <classpathentry exported="true" kind="lib" path="/ant.jar" sourcepath="/ant-src.jar">
                        <attributes>
                            <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                            <attribute name="javadoc_location" value="jar:file:/ant-javadoc.jar!/path"/>
                        </attributes>
                        <accessrules>
                            <accessrule kind="nonaccessible" pattern="secret**"/>
                        </accessrules>
                    </classpathentry>'''

    def canReadFromXml() {
        when:
        Library library = new Library(new XmlParser().parseText(XML_TEXT))

        then:
        library == createLibrary()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createLibrary().appendNode(rootNode)

        then:
        new Library(rootNode.classpathentry[0]) == createLibrary()
    }

    def equality() {
        Library library = createLibrary()
        library.javadocPath += 'x'

        expect:
        library != createLibrary()
    }

    private Library createLibrary() {
        return new Library('/ant.jar', true, 'mynative', [new AccessRule('nonaccessible', 'secret**')] as Set,
                "/ant-src.jar", "jar:file:/ant-javadoc.jar!/path")
    }
}