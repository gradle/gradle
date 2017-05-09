/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.initialization

import org.gradle.api.Project
import org.gradle.api.initialization.ProjectDescriptor
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.scripts.DefaultScriptFileResolver
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.scripts.ScriptingLanguage
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification
import spock.lang.Unroll

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.is
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertThat
import static org.junit.Assert.assertTrue

class DefaultProjectDescriptorTest extends Specification {

    @Rule
    final TestName testName = new TestName()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def fileResolver = TestFiles.resolver(tmpDir.testDirectory)
    def descriptorRegistry = new DefaultProjectDescriptorRegistry()

    def "can set project name"() {
        given:
        def descriptor = projectDescriptor()
        def registry = Mock(ProjectDescriptorRegistry)
        descriptor.setProjectDescriptorRegistry(registry)

        when:
        descriptor.name = "newName"

        then:
        1 * registry.changeDescriptorPath(Path.path(Project.PATH_SEPARATOR + testName.methodName), Path.path(Project.PATH_SEPARATOR + "newName"))
        descriptor.name == "newName"
    }

    def "can set relative project directory"() {
        given:
        def descriptor = projectDescriptor()

        when:
        descriptor.projectDir = new File("relative/path")

        then:
        descriptor.projectDir.absolutePath == new File(tmpDir.testDirectory, "relative/path").absolutePath
    }

    def "can set absolute project directory"() {
        given:
        def descriptor = projectDescriptor()

        when:
        descriptor.projectDir = new File("absolute/path").absoluteFile

        then:
        descriptor.projectDir.absolutePath == new File("absolute/path").absolutePath
    }

    def "build file is built from build filename and project dir"() {
        given:
        def descriptor = projectDescriptor()

        when:
        descriptor.buildFileName = 'project.gradle'

        then:
        descriptor.buildFile == new File(tmpDir.testDirectory, 'project.gradle').canonicalFile
    }

    def "different root"() {
        given:
        def descriptor = projectDescriptor()

        and:
        def parentDescriptor = new DefaultProjectDescriptor(null, "other", new File("other"), descriptorRegistry, fileResolver)
        def otherDescriptor = new DefaultProjectDescriptor(parentDescriptor, testName.methodName, tmpDir.testDirectory, descriptorRegistry, fileResolver)

        expect:
        descriptor != otherDescriptor
    }

    @Unroll
    def "build file name is resolved to existing #buildFilename with script languages for #extensions"() {
        given:
        def descriptor = projectDescriptor(scriptFileResolver(extensions))

        and:
        def buildFile = tmpDir.createFile(buildFilename)

        expect:
        descriptor.buildFileName == buildFile.name
        descriptor.buildFile.absolutePath == buildFile.absolutePath

        where:
        buildFilename      | extensions
        'build.gradle'     | []
        'build.gradle'     | ['.gradle.kts']
        'build.gradle.kts' | ['.gradle.kts']
        'build.gradle'     | ['.gradle.kts', '.tic']
        'build.gradle.kts' | ['.gradle.kts', '.tac']
        'build.gradle'     | ['.tic', '.gradle.kts']
        'build.gradle.kts' | ['.tac', '.gradle.kts']
    }

    private ProjectDescriptor projectDescriptor(ScriptFileResolver scriptFileResolver = null) {
        def parentDescriptor = new DefaultProjectDescriptor(null, "somename", new File("somefile"), descriptorRegistry, fileResolver, scriptFileResolver)
        def descriptor = new DefaultProjectDescriptor(parentDescriptor, testName.methodName, tmpDir.testDirectory, descriptorRegistry, fileResolver, scriptFileResolver)
        assertSame(parentDescriptor, descriptor.parent)
        assertThat(parentDescriptor.children.size(), is(1))
        assertTrue(parentDescriptor.children.contains(descriptor))
        assertSame(descriptor.projectDescriptorRegistry, descriptorRegistry)
        assertThat(descriptor.name, equalTo(testName.methodName))
        assertThat(descriptor.projectDir, equalTo(tmpDir.testDirectory.canonicalFile))
        assertThat(descriptor.buildFileName, equalTo(Project.DEFAULT_BUILD_FILE))
        assertThat(parentDescriptor.path, equalTo(Project.PATH_SEPARATOR))
        assertThat(descriptor.path, equalTo(Project.PATH_SEPARATOR + descriptor.name))
        return descriptor
    }

    private ScriptFileResolver scriptFileResolver(List<String> extensions) {
        DefaultScriptFileResolver.forScriptingLanguages(extensions.collect { extension ->
            Stub(ScriptingLanguage) { getExtension() >> extension }
        })
    }
}
