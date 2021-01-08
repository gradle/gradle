/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.plugins.internal.DefaultBasePluginConvention
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Before
import org.junit.Rule
import org.junit.Test

import static org.junit.Assert.assertEquals

class DefaultBasePluginConventionTest {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance(getClass())

    private ProjectInternal project = TestUtil.create(temporaryFolder).rootProject()
    private BasePluginConvention convention

    @Before void setUp() {
        convention = new DefaultBasePluginConvention(project)
    }

    @Test void defaultValues() {
        assertEquals(project.name, convention.archivesBaseName)
        assertEquals('distributions', convention.distsDirName)
        assertEquals(new File(project.buildDir, 'distributions'), convention.distsDirectory.getAsFile().get())
        assertEquals('libs', convention.libsDirName)
        assertEquals(new File(project.buildDir, 'libs'), convention.libsDirectory.getAsFile().get())
    }

    @Test void dirsRelativeToBuildDir() {
        project.buildDir = project.file('mybuild')
        convention.distsDirName = 'mydists'
        assertEquals(project.file('mybuild/mydists'), convention.distsDirectory.getAsFile().get())
        convention.libsDirName = 'mylibs'
        assertEquals(project.file('mybuild/mylibs'), convention.libsDirectory.getAsFile().get())
    }

    @Test void dirsAreCachedProperly() {
        project.buildDir = project.file('mybuild')
        convention.distsDirName = 'mydists'
        assertEquals(project.file('mybuild/mydists'), convention.distsDirectory.getAsFile().get())
        convention.libsDirName = 'mylibs'
        assertEquals(project.file('mybuild/mylibs'), convention.libsDirectory.getAsFile().get())
        convention.distsDirName = 'mydists2'
        assertEquals(project.file('mybuild/mydists2'), convention.distsDirectory.getAsFile().get())
        convention.libsDirName = 'mylibs2'
        assertEquals(project.file('mybuild/mylibs2'), convention.libsDirectory.getAsFile().get())
        project.buildDir = project.file('mybuild2')
        assertEquals(project.file('mybuild2/mylibs2'), convention.libsDirectory.getAsFile().get())
    }
}
