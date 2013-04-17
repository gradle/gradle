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
package org.gradle.initialization;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.project.ProjectRegistry;
import org.gradle.api.internal.project.ProjectIdentifier;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.gradle.util.GFileUtils;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(JMock.class)
public class ProjectDirectoryProjectSpecTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();
    private final File dir = tmpDir.createDir("build");
    private final ProjectDirectoryProjectSpec spec = new ProjectDirectoryProjectSpec(dir);
    private int counter;

    @Test
    public void containsMatchWhenAtLeastOneProjectHasSpecifiedProjectDir() {
        assertFalse(spec.containsProject(registry()));
        assertFalse(spec.containsProject(registry(project(new File("other")))));

        assertTrue(spec.containsProject(registry(project(dir))));
        assertTrue(spec.containsProject(registry(project(dir), project(new File("other")))));
        assertTrue(spec.containsProject(registry(project(dir), project(dir))));
    }

    @Test
    public void selectsSingleProjectWhichHasSpecifiedProjectDir() {
        ProjectIdentifier project = project(dir);
        assertThat(spec.selectProject(registry(project, project(new File("other")))), sameInstance(project));
    }

    @Test
    public void selectProjectFailsWhenNoProjectHasSpecifiedProjectDir() {
        try {
            spec.selectProject(registry());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("No projects in this build have project directory '" + dir + "'."));
        }
    }

    @Test
    public void selectProjectFailsWhenMultipleProjectsHaveSpecifiedProjectDir() {
        ProjectIdentifier project1 = project(dir);
        ProjectIdentifier project2 = project(dir);
        try {
            spec.selectProject(registry(project1, project2));
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), startsWith("Multiple projects in this build have project directory '" + dir + "':"));
        }
    }

    @Test
    public void cannotSelectProjectWhenBuildFileIsNotAFile() {
        dir.delete();

        try {
            spec.containsProject(registry());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Project directory '" + dir + "' does not exist."));
        }

        GFileUtils.writeStringToFile(dir, "file");

        try {
            spec.containsProject(registry());
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Project directory '" + dir + "' is not a directory."));
        }
    }

    private ProjectRegistry<ProjectIdentifier> registry(final ProjectIdentifier... projects) {
        final ProjectRegistry<ProjectIdentifier> registry = context.mock(ProjectRegistry.class, String.valueOf(counter++));
        context.checking(new Expectations(){{
            allowing(registry).getAllProjects();
            will(returnValue(toSet(projects)));
        }});
        return registry;
    }

    private ProjectIdentifier project(final File projectDir) {
        final ProjectIdentifier projectIdentifier = context.mock(ProjectIdentifier.class, String.valueOf(counter++));
        context.checking(new Expectations(){{
            allowing(projectIdentifier).getProjectDir();
            will(returnValue(projectDir));
        }});
        return projectIdentifier;
    }
}
