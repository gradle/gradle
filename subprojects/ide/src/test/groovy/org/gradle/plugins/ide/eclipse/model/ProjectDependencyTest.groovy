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


class ProjectDependencyTest extends Specification {
    final static String XML_TEXT = '''
                <classpathentry kind="src" path="/test2" exported="true">
                    <attributes>
                        <attribute name="org.eclipse.jdt.launching.CLASSPATH_ATTR_LIBRARY_PATH_ENTRY" value="mynative"/>
                    </attributes>
                    <accessrules>
                        <accessrule kind="nonaccessible" pattern="secret**"/>
                    </accessrules>
                </classpathentry>'''

    def canReadFromXml() {
        when:
        ProjectDependency projectDependency = new ProjectDependency(new XmlParser().parseText(XML_TEXT))

        then:
        projectDependency == createProjectDependency()
    }

    def canWriteToXml() {
        Node rootNode = new Node(null, 'root')

        when:
        createProjectDependency().appendNode(rootNode)

        then:
        new ProjectDependency(rootNode.classpathentry[0]) == createProjectDependency()
    }

    def equality() {
        ProjectDependency projectDependency = createProjectDependency()
        projectDependency.nativeLibraryLocation += 'x'

        expect:
        projectDependency != createProjectDependency()
    }

    private ProjectDependency createProjectDependency() {
        ProjectDependency dependency = new ProjectDependency('/test2')
        dependency.exported = true
        dependency.nativeLibraryLocation = 'mynative'
        dependency.accessRules += [new AccessRule('nonaccessible', 'secret**')]
        return dependency
    }
}
