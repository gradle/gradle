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

import org.gradle.api.Project;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.util.Path;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class DefaultProjectDescriptorTest {
    private DefaultProjectDescriptor projectDescriptor;
    private DefaultProjectDescriptor parentProjectDescriptor;
    private static final String TEST_NAME = "testName";
    private static final File TEST_DIR = new File("testDir");
    private static final FileResolver FILE_RESOLVER = TestFiles.resolver(TEST_DIR.getAbsoluteFile());
    private DefaultProjectDescriptorRegistry testProjectDescriptorRegistry;
    private JUnit4Mockery context = new JUnit4Mockery();

    @Before
    public void setUp() {
        testProjectDescriptorRegistry = new DefaultProjectDescriptorRegistry();
        parentProjectDescriptor = new DefaultProjectDescriptor(null, "somename", new File("somefile"),
                testProjectDescriptorRegistry, FILE_RESOLVER);
        projectDescriptor = new DefaultProjectDescriptor(parentProjectDescriptor, TEST_NAME, TEST_DIR,
                testProjectDescriptorRegistry, FILE_RESOLVER);
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
            one(projectDescriptorRegistryMock).changeDescriptorPath(Path.path(Project.PATH_SEPARATOR + TEST_NAME), Path.path(Project.PATH_SEPARATOR + newName));
        }});
        projectDescriptor.setName(newName);
        assertEquals(newName, projectDescriptor.getName());
    }

    @Test
    public void setProjectDirRelative() {
        final ProjectDescriptorRegistry projectDescriptorRegistryMock = context.mock(ProjectDescriptorRegistry.class);
        projectDescriptor.setProjectDescriptorRegistry(projectDescriptorRegistryMock);
        projectDescriptor.setProjectDir(new File("relative/path"));
        final String expectedPath = new File(TEST_DIR, "relative/path").getAbsolutePath();
        assertEquals(expectedPath, projectDescriptor.getProjectDir().getAbsolutePath());
    }

    @Test
    public void setProjectDirAbsolute() {
        final ProjectDescriptorRegistry projectDescriptorRegistryMock = context.mock(ProjectDescriptorRegistry.class);
        projectDescriptor.setProjectDescriptorRegistry(projectDescriptorRegistryMock);
        String absolutePath = new File("absolute/path").getAbsolutePath();
        projectDescriptor.setProjectDir(new File(absolutePath));
        assertEquals(absolutePath, projectDescriptor.getProjectDir().getAbsolutePath());
    }

    @Test
    public void buildFileIsBuiltFromBuildFileNameAndProjectDir() throws IOException {
        projectDescriptor.setBuildFileName("project.gradle");
        assertEquals(new File(TEST_DIR, "project.gradle").getCanonicalFile(), projectDescriptor.getBuildFile());
    }
}
