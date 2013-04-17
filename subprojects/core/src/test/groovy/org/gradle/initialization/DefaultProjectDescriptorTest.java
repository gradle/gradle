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
package org.gradle.initialization;

import org.gradle.util.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.gradle.api.Project;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.Expectations;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultProjectDescriptorTest {
    private DefaultProjectDescriptor projectDescriptor;
    private DefaultProjectDescriptor parentProjectDescriptor;
    private static final String TEST_NAME = "testName";
    private static final File TEST_DIR = new File("testDir");
    private DefaultProjectDescriptorRegistry testProjectDescriptorRegistry;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        testProjectDescriptorRegistry = new DefaultProjectDescriptorRegistry();
        parentProjectDescriptor = new DefaultProjectDescriptor(null, "somename", new File("somefile"),
                testProjectDescriptorRegistry);
        projectDescriptor = new DefaultProjectDescriptor(parentProjectDescriptor, TEST_NAME, TEST_DIR,
                testProjectDescriptorRegistry);
    }

    @Test
    public void init() throws IOException {
        assertSame(parentProjectDescriptor, projectDescriptor.getParent());
        assertEquals(1, parentProjectDescriptor.getChildren().size());
        assertTrue(parentProjectDescriptor.getChildren().contains(projectDescriptor));
        assertSame(testProjectDescriptorRegistry, projectDescriptor.getProjectDescriptorRegistry());
        assertEquals(TEST_NAME, projectDescriptor.getName());
        assertEquals(TEST_DIR.getCanonicalFile(), projectDescriptor.getProjectDir());
        assertEquals(Project.DEFAULT_BUILD_FILE, projectDescriptor.getBuildFileName());
        checkPath();
    }

    private void checkPath() {
        assertEquals(Project.PATH_SEPARATOR, parentProjectDescriptor.getPath());
        assertEquals(Project.PATH_SEPARATOR + projectDescriptor.getName(), projectDescriptor.getPath());
    }

    @Test
    public void setName() {
        final String newName = "newName";
        final ProjectDescriptorRegistry projectDescriptorRegistryMock = context.mock(ProjectDescriptorRegistry.class);
        projectDescriptor.setProjectDescriptorRegistry(projectDescriptorRegistryMock);
        context.checking(new Expectations() {{
            one(projectDescriptorRegistryMock).changeDescriptorPath(Path.path(TEST_NAME), Path.path(Project.PATH_SEPARATOR + newName));
        }});
        projectDescriptor.setName(newName);
        assertEquals(newName, projectDescriptor.getName());
    }

    @Test
    public void buildFileIsBuiltFromBuildFileNameAndProjectDir() throws IOException {
        projectDescriptor.setBuildFileName("project.gradle");
        assertEquals(new File(TEST_DIR, "project.gradle").getCanonicalFile(), projectDescriptor.getBuildFile());
    }
}
