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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.plugins.AbstractPluginConventionTest
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.ReportingBasePluginConvention
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertEquals
import org.gradle.api.JavaVersion

/**
 * @author Hans Dockter
 */
class BasePluginConventionTest extends AbstractPluginConventionTest {
    private BasePluginConvention convention

    Class getType() {
        BasePluginConvention
    }

    Map getCustomValues() {
        [distsDirName: 'distributions']
    }

    @Before public void setUp() {
        super.setUp()
        convention = new BasePluginConvention(project, [:])
    }

    @Test public void defaultValues() {
        assertEquals('distributions', convention.distsDirName)
        assertEquals(new File(project.buildDir, 'distributions'), convention.distsDir)
        assertEquals('', convention.libsDirName)
        assertEquals(project.buildDir, convention.libsDir)
    }

    @Test public void dirsRelativeToBuildDir() {
        project.buildDirName = 'mybuild'
        convention.distsDirName = 'mydists'
        assertEquals(project.file('mybuild/mydists'), convention.distsDir)
        convention.libsDirName = 'mylibs'
        assertEquals(project.file('mybuild/mylibs'), convention.libsDir)
    }
}
