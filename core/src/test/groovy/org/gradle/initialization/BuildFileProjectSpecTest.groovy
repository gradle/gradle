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
package org.gradle.initialization

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

@CleanupTestDirectory
class BuildFileProjectSpecTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())
    private File file
    private File otherFile
    private BuildFileProjectSpec spec

    def setup() {
        file = temporaryFolder.createFile("build")
        otherFile = new File("other")
        spec = new BuildFileProjectSpec(file)
    }

    def "contains match when at least one project has specified build file"() {
        given:
        expect:
        !spec.containsProject(registry())
        !spec.containsProject(registry(project(otherFile)))

        and:
        spec.containsProject(registry(project(file)))
        spec.containsProject(registry(project(file), project(otherFile)))
        spec.containsProject(registry(project(file), project(file)))
    }

    def "selects single project which has specific build file"() {
        when:
        def target = project(file)

        then:
        spec.selectProject("description", registry(target, project(otherFile))).is(target)
    }

    def "selectProject() throws when no project has specified build file"() {
        when:
        spec.selectProject("settings 'foo'", registry())

        then:
        InvalidUserDataException e = thrown()
        e.message == "Build file '$file' is not part of the build defined by settings 'foo'. If this is an unrelated build, it must have its own settings file."
    }

    def "selectProject() throws when multiple projects have specified build file"() {
        when:
        spec.selectProject("settings 'foo'", registry(project(file), project(file)))

        then:
        InvalidUserDataException e = thrown()
        e.message.startsWith("Multiple projects in this build have build file '$file':")
    }

    def "selectProject() throws when build file is not a file."() {
        when:
        file.delete()
        spec.containsProject(registry())

        then:
        InvalidUserDataException e = thrown()
        e.message == "Build file '$file' does not exist.".toString()

        when:
        file.mkdirs()
        spec.containsProject(registry())

        then:
        e = thrown()
        e.message == "Build file '$file' is not a file.".toString()
    }

    private ProjectRegistry<ProjectIdentifier> registry(ProjectIdentifier... projects) {
        ProjectRegistry<ProjectIdentifier> registry = Stub() {
            getAllProjects() >> (projects as Set)
        }
        return registry
    }

    private ProjectIdentifier project(File buildFile) {
        return Stub(ProjectIdentifier) {
            getBuildFile() >> buildFile
        }
    }
}
