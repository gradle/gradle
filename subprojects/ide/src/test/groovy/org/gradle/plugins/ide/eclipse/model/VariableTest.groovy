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

import spock.lang.Specification

/**
 * @author Hans Dockter
 */

class VariableTest extends Specification {
    final static String XML_TEXT = '''
                <classpathentry exported="true" kind="var" path="/GRADLE_CACHE/ant.jar" sourcepath="/GRADLE_CACHE/ant-src.jar">
                    <attributes>
                        <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                        <attribute name="javadoc_location" value="jar:file:/GRADLE_CACHE/ant-javadoc.jar!/path"/>
                    </attributes>
                    <accessrules>
                        <accessrule kind="nonaccessible" pattern="secret**"/>
                    </accessrules>
                </classpathentry>'''

    def canReadFromXml() {
        when:
        Variable variable = new Variable(new XmlParser().parseText(XML_TEXT))

        then:
        variable == createVariable()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createVariable().appendNode(rootNode)

        then:
        new Variable(rootNode.classpathentry[0]) == createVariable()
    }

    def equality() {
        Variable variable = createVariable()
        variable.sourcePath += 'x'

        expect:
        variable != createVariable()
    }

    private Variable createVariable() {
        return new Variable('/GRADLE_CACHE/ant.jar', true, 'mynative', [new AccessRule('nonaccessible', 'secret**')] as Set,
                "/GRADLE_CACHE/ant-src.jar", "jar:file:/GRADLE_CACHE/ant-javadoc.jar!/path")
    }


}