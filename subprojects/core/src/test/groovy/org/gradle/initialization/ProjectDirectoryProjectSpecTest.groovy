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

import static org.gradle.util.internal.WrapUtil.toSet

@CleanupTestDirectory
public class ProjectDirectoryProjectSpecTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass());
    private final File dir = temporaryFolder.createDir("build");
    private final ProjectDirectoryProjectSpec spec = new ProjectDirectoryProjectSpec(dir);
    private int counter;
    def settings = "settings 'foo'"

    def "contains match when at least one project has specified project dir"() {
        expect:
        !spec.containsProject(registry())
        !spec.containsProject(registry(project(new File("other"))))

        spec.containsProject(registry(project(dir)))
        spec.containsProject(registry(project(dir), project(new File("other"))))
        spec.containsProject(registry(project(dir), project(dir)))
    }

    def "selects single project which has specified project dir"() {
        when:
        ProjectIdentifier project1 = project(dir);

        then:
        spec.selectProject("settings 'foo'", registry(project1, project(new File("other")))) == project1;
    }

    def "select project fails when no project has specified project dir"() {
        when:
        spec.selectProject("settings 'foo'", registry())

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Project directory '$dir' is not part of the build defined by settings 'foo'. If this is an unrelated build, it must have its own settings file."
    }

    def "select project fails when multiple projects have specified project dir"() {
        ProjectIdentifier project1 = project(dir);
        ProjectIdentifier project2 = project(dir);

        when:
        spec.selectProject("settings 'foo'", registry(project1, project2));

        then:
        def e = thrown(InvalidUserDataException)
        e.message.startsWith "Multiple projects in this build have project directory '" + dir + "':"
    }

    def "cannot select project when build file is not a file"() {
        dir.delete();

        when:
        spec.containsProject(registry());

        then:
        def e = thrown(InvalidUserDataException)
        e.message == "Project directory '" + dir + "' does not exist."

        when:
        dir.text = "file"
        spec.containsProject(registry());

        then:
        e = thrown(InvalidUserDataException)
        e.message == "Project directory '" + dir + "' is not a directory."
    }

    private ProjectRegistry<ProjectIdentifier> registry(final ProjectIdentifier... projects) {
        final ProjectRegistry<ProjectIdentifier> registry = Stub(ProjectRegistry)
        registry.getAllProjects() >> toSet(projects)
        return registry
    }

    private ProjectIdentifier project(final File projectDir) {
        final ProjectIdentifier projectIdentifier = Mock(ProjectIdentifier)
        projectIdentifier.projectDir >> projectDir
        return projectIdentifier
    }
}
