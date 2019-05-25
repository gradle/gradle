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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.util.Path;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

public class DefaultProjectDescriptorRegistryTest {
    private static final File TEST_DIR = new File("testDir");

    private static final FileResolver FILE_RESOLVER = TestFiles.resolver(TEST_DIR.getAbsoluteFile());
    private final DefaultProjectDescriptorRegistry registry = new DefaultProjectDescriptorRegistry();

    @Test
    public void addProjectDescriptor() {
        DefaultProjectDescriptor rootProject = new DefaultProjectDescriptor(null, "testName", TEST_DIR, registry, FILE_RESOLVER);

        registry.addProject(rootProject);
        assertSame(rootProject, registry.getProject(rootProject.getPath()));
        assertSame(rootProject, registry.getProject(rootProject.getProjectDir()));
    }

    @Test
    public void changeProjectDescriptorPath() {
        DefaultProjectDescriptor project = new DefaultProjectDescriptor(null, "name", TEST_DIR, registry, FILE_RESOLVER);
        registry.addProject(project);

        registry.changeDescriptorPath(Path.path(":"), Path.path(":newPath"));
        assertThat(registry.getRootProject(), nullValue());
        assertThat(registry.getProject(":"), nullValue());
        assertThat(registry.getProject(":newPath"), sameInstance(project));
    }
}
