/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.tasks.ide.eclipse;

import org.apache.commons.io.IOUtils;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.GFileUtils;
import org.hamcrest.Matchers;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class EclipseProjectTest extends AbstractTaskTest {
    private EclipseProject eclipseProject;

    public AbstractTask getTask() {
        return eclipseProject;
    }

    @Before
    public void setUp() {
        super.setUp();
        eclipseProject = createTask(EclipseProject.class);
        eclipseProject.setProjectName("myProject");
    }

    @Test
    public void generateJavaProject() throws IOException {
        eclipseProject.setProjectType(ProjectType.JAVA);
        eclipseProject.execute();
        checkProjectFile("expectedJavaProjectFile.txt");
    }

    @Test
    public void generateJavaProjectWithDuplicateNature() throws IOException {
        eclipseProject.setProjectType(ProjectType.JAVA);
        eclipseProject.getNatureNames().add("org.eclipse.jdt.core.javanature");
        eclipseProject.execute();
        checkProjectFile("expectedJavaProjectFile.txt");
    }

    @Test
    public void generateJavaProjectWithCustomBuilder() throws IOException {
        eclipseProject.setProjectType(ProjectType.JAVA);
		eclipseProject.getBuildCommandNames().add("org.gradle.test.custom.custombuilder");
        eclipseProject.execute();
        checkProjectFile("expectedJavaProjectFileWithCustomBuilder.txt");
    }

    @Test
    public void generateSimpleProject() throws IOException {
        eclipseProject.setProjectType(ProjectType.SIMPLE);
        eclipseProject.execute();
        checkProjectFile("expectedSimpleProjectFile.txt");
    }

    @Test
    public void generateSimpleProjectWithCustomNature() throws IOException {
        eclipseProject.setProjectType(ProjectType.SIMPLE);
        eclipseProject.getNatureNames().add("org.gradle.test.natures.CustomNature");
        eclipseProject.execute();

        checkProjectFile("expectedSimpleProjectFileWithCustomNature.txt");
    }

    private void checkProjectFile(String expectedResourcePath) throws IOException {
        File project = new File(getProject().getProjectDir(), EclipseProject.PROJECT_FILE_NAME);
        assertTrue(project.isFile());
        assertThat(GFileUtils.readFileToString(project),
                Matchers.equalTo(IOUtils.toString(this.getClass().getResourceAsStream(expectedResourcePath))));
    }
}
