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

class SourceFolderTest extends Specification {
    final static String XML_TEXT = '''
                <classpathentry including="**/Test1*|**/Test2*" excluding="**/Test3*|**/Test4*" kind="src" output="bin2" path="src">
                    <attributes>
                        <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                    </attributes>
                    <accessrules>
                        <accessrule kind="nonaccessible" pattern="secret**"/>
                    </accessrules>
                </classpathentry>'''

    def canReadFromXml() {
        expect:
        new SourceFolder(new XmlParser().parseText(XML_TEXT)) == createSourceFolder()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createSourceFolder().appendNode(rootNode)

        then:
        new SourceFolder(rootNode.classpathentry[0]) == createSourceFolder()
    }

    def equality() {
        SourceFolder sourceFolder = createSourceFolder()
        sourceFolder.nativeLibraryLocation += 'x'

        expect:
        sourceFolder != createSourceFolder()
    }

    def createSourceFolder() {
        SourceFolder folder = new SourceFolder('src', 'bin2')
        folder.nativeLibraryLocation = 'mynative'
        folder.accessRules += [new AccessRule('nonaccessible', 'secret**')]
        folder.includes += ['**/Test1*', '**/Test2*']
        folder.excludes += ['**/Test3*', '**/Test4*']
        return folder
    }

    def "ignores null dir in equality"() {
        given:
        def one = createSourceFolder()
        def two = createSourceFolder()
        one == two
        two == one

        when:
        one.dir = null
        two.dir = new File('.')

        then:
        one == two
        two == one
    }

    def "trims path"() {
        given:
        def one = createSourceFolder()
        one.dir = new File('/some/path/to/foo')
        one.name = "foo"
        when:
        one.trim()

        then:
        one.path == 'foo'
    }

    def "trims path with provided prefix"() {
        given:
        def one = createSourceFolder()
        one.dir = new File('/some/path/to/foo')
        one.name = "foo"
        when:
        one.trim("prefix")

        then:
        one.path == 'prefix-foo'
        one.name== 'prefix-foo'
    }
}
