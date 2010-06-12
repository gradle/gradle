/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.AbstractTaskTest;
import org.gradle.util.TestFile;
import org.gradle.util.Resources;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class EclipseProjectTest extends AbstractTaskTest {
    @Rule
    public final Resources resources = new Resources();
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
    public void generateEmptyProject() throws IOException {
        eclipseProject.execute();
        checkProjectFile("expectedEmptyProjectFile.txt");
    }

    @Test
    public void generateProjectWithNatures() throws IOException {
        eclipseProject.getNatureNames().add("org.gradle.test.natures.CustomNature1");
        eclipseProject.getNatureNames().add("org.gradle.test.natures.CustomNature2");
        eclipseProject.execute();

        checkProjectFile("expectedProjectFileWithCustomNature.txt");
    }

    @Test
    public void generateProjectWithBuilders() throws IOException {
		eclipseProject.getBuildCommandNames().add("org.gradle.test.custom.custombuilder1");
		eclipseProject.getBuildCommandNames().add("org.gradle.test.custom.custombuilder2");
        eclipseProject.execute();
        checkProjectFile("expectedProjectFileWithCustomBuilder.txt");
    }

    private void checkProjectFile(String expectedResourcePath) throws IOException {
        TestFile project = new TestFile(getProject().getProjectDir(), EclipseProject.PROJECT_FILE_NAME);
        project.assertIsFile();
        project.assertContents(Matchers.equalTo(resources.getResource(expectedResourcePath).getText()));
        assertTrue(project.isFile());
    }
}
