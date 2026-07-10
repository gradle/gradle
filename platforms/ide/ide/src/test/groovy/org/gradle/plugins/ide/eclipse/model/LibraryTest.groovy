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
import org.gradle.plugins.ide.eclipse.model.internal.FileReferenceFactory
import org.gradle.util.Matchers
import spock.lang.Specification


class LibraryTest extends Specification {
    final static String XML_TEXT_TEMPLATE = '''
                    <classpathentry exported="true" kind="lib" path="/ant.jar" sourcepath="/ant-src.jar">
                        <attributes>
                            <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                            <attribute name="javadoc_location" value="jar:%FILE_URI%!/"/>
                        </attributes>
                        <accessrules>
                            <accessrule kind="nonaccessible" pattern="secret**"/>
                        </accessrules>
                    </classpathentry>'''
    final fileReferenceFactory = new FileReferenceFactory()

    String platformXml;

    def setup(){
        //xml differs on windows and mac due to required absolute paths for javadoc uri
        platformXml = XML_TEXT_TEMPLATE.replace("%FILE_URI%", new File("ant-javadoc.jar").toURI().toString());
    }

    def canReadFromXml() {
        when:
        Library library = new Library(new XmlParser().parseText(platformXml), fileReferenceFactory)

        then:
        library == createLibrary()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createLibrary().appendNode(rootNode)

        then:
        new Library(rootNode.classpathentry[0], fileReferenceFactory) == createLibrary()
    }

    def equality() {
        Library library = createLibrary()
        Library same = createLibrary()
        Library differentPath = createLibrary()
        differentPath.path = '/other'

        expect:
        library Matchers.strictlyEqual(same)
        library != differentPath
    }

    private Library createLibrary() {
        Library library = new Library(fileReferenceFactory.fromPath('/ant.jar'))
        library.exported = true
        library.nativeLibraryLocation = 'mynative'
        library.accessRules += [new AccessRule('nonaccessible', 'secret**')]
        library.sourcePath = fileReferenceFactory.fromPath("/ant-src.jar")
        library.javadocPath = fileReferenceFactory.fromJarURI("jar:${new File("ant-javadoc.jar").toURI()}!/");
        return library
    }
}
