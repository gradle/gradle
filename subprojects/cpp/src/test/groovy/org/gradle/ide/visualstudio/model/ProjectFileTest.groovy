/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.ide.visualstudio.model

import org.gradle.api.Transformer
import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import spock.lang.Specification

class ProjectFileTest extends Specification {
    TestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()
    Transformer<String, File> fileNameTransformer = { it.name } as Transformer<String, File>
    def projectFile = new ProjectFile(fileNameTransformer)

    def "setup"() {
        projectFile.loadDefaults()
    }

    def "empty project file"() {
        expect:
        itemGroup('ProjectConfigurations').children().isEmpty()
        itemGroup('Sources').children().isEmpty()
        itemGroup('Headers').children().isEmpty()
    }

    def "set project uuid"() {
        when:
        projectFile.setProjectUuid("THE_PROJECT_UUID")

        then:
        globals.ProjectGUID[0].text() == "THE_PROJECT_UUID"
    }

    def "add source and headers"() {
        when:
        projectFile.addSourceFile(file("sourceOne"))
        projectFile.addSourceFile(file("sourceTwo"))

        projectFile.addHeaderFile(file("headerOne"))
        projectFile.addHeaderFile(file("headerTwo"))

        then:
        assert sourceFile(0) == "sourceOne"
        assert sourceFile(1) == "sourceTwo"

        assert headerFile(0) == "headerOne"
        assert headerFile(1) == "headerTwo"
    }

    private String sourceFile(int index) {
        def source = itemGroup('Sources').ClCompile[index]
        return source.'@Include'
    }

    private String headerFile(int index) {
        def header = itemGroup('Headers').ClInclude[index]
        return header.'@Include'
    }

    private Node itemGroup(String label) {
        return projectXml.ItemGroup.find({it.'@Label' == label}) as Node
    }

    private Node getGlobals() {
        return projectXml.PropertyGroup.find({it.'@Label' == 'Globals'}) as Node
    }

    private def getProjectXml() {
        return new XmlParser().parse(projectFileContent)
    }

    private TestFile getProjectFileContent() {
        def file = testDirectoryProvider.testDirectory.file("project.xml")
        projectFile.store(file)
        return file
    }

    private TestFile file(String name) {
        testDirectoryProvider.testDirectory.file(name)
    }
}