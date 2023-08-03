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
import org.gradle.internal.scripts.ScriptFileResolver
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Path
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.CoreMatchers.is
import static org.junit.Assert.assertSame
import static org.hamcrest.MatcherAssert.assertThat
import static org.junit.Assert.assertTrue

class DefaultProjectDescriptorTest extends Specification {

    @Rule
    final TestName testName = new TestName()

    @Rule
    final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    final TestFile testDirectory = tmpDir.testDirectory

    def fileResolver = TestFiles.resolver(testDirectory)
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
        descriptor.projectDir.absolutePath == new File(testDirectory, "relative/path").absolutePath
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
        descriptor.buildFile == new File(testDirectory, 'project.gradle').canonicalFile
    }

    def "different root"() {
        given:
        def descriptor = projectDescriptor()

        and:
        def otherRegistry = new DefaultProjectDescriptorRegistry()
        def parentDescriptor = new DefaultProjectDescriptor(null, "other", new File("other"), otherRegistry, fileResolver)
        def otherDescriptor = new DefaultProjectDescriptor(parentDescriptor, testName.methodName, testDirectory, otherRegistry, fileResolver)

        expect:
        descriptor != otherDescriptor
    }

    def "build file name is resolved by given ScriptFileResolver"() {
        given:
        def scriptFileResolver = Mock(ScriptFileResolver)
        def descriptor = projectDescriptor(scriptFileResolver)

        and:
        def expectedBuildFile = tmpDir.createFile(buildFilename)

        when:
        def foundBuildFile = descriptor.buildFile

        then:
        1 * scriptFileResolver.resolveScriptFile(testDirectory, 'build') >> expectedBuildFile

        expect:
        foundBuildFile == expectedBuildFile

        where:
        buildFilename << ['build.gradle', 'build.gradle.kts']
    }

    private ProjectDescriptor projectDescriptor(ScriptFileResolver scriptFileResolver = null) {
        def parentDescriptor = new DefaultProjectDescriptor(null, "somename", new File("somefile"), descriptorRegistry, fileResolver, scriptFileResolver)
        def descriptor = new DefaultProjectDescriptor(parentDescriptor, testName.methodName, testDirectory, descriptorRegistry, fileResolver, scriptFileResolver)
        assertSame(parentDescriptor, descriptor.parent)
        assertThat(parentDescriptor.children.size(), is(1))
        assertTrue(parentDescriptor.children.contains(descriptor))
        assertSame(descriptor.projectDescriptorRegistry, descriptorRegistry)
        assertThat(descriptor.name, equalTo(testName.methodName))
        assertThat(descriptor.projectDir, equalTo(testDirectory.canonicalFile))
        assertThat(descriptor.buildFileName, equalTo(Project.DEFAULT_BUILD_FILE))
        assertThat(parentDescriptor.path, equalTo(Project.PATH_SEPARATOR))
        assertThat(descriptor.path, equalTo(Project.PATH_SEPARATOR + descriptor.name))
        return descriptor
    }
}
