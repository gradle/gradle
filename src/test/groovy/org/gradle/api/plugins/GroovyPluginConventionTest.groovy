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

import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

/**
 * @author Hans Dockter
 */
class GroovyPluginConventionTest extends AbstractPluginConventionTest {
    private GroovyPluginConvention groovyConvention

    Class getType() {
        GroovyPluginConvention
    }

    Map getCustomValues() {
        [groovySrcDirNames: ['newSourceRootName']]
    }

    void setUp() {
        super.setUp()
        project.convention.plugins.java = new JavaPluginConvention(project, [:])
        groovyConvention = new GroovyPluginConvention(project, [:])
    }

    void testGroovyConvention() {
        assertEquals(['main/groovy'], groovyConvention.groovySrcDirNames)
        assertEquals(['test/groovy'], groovyConvention.groovyTestSrcDirNames)
        assertEquals([], groovyConvention.floatingGroovySrcDirs)
        assertEquals([], groovyConvention.floatingGroovyTestSrcDirs)
    }

    void testGroovyDefaultDirs() {
        checkGroovyDirs(project.srcRootName)
    }

    void testGroovyDynamicDirs() {
        project.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkGroovyDirs(project.srcRootName)
    }

    private void checkGroovyDirs(String srcRootName) {
        groovyConvention.floatingGroovySrcDirs << 'someGroovySrcDir' as File
        groovyConvention.floatingGroovyTestSrcDirs <<'someGroovyTestSrcDir' as File
        assertEquals([new File(project.srcRoot, groovyConvention.groovySrcDirNames[0])] + groovyConvention.floatingGroovySrcDirs,
                groovyConvention.groovySrcDirs)
        assertEquals([new File(project.srcRoot, groovyConvention.groovyTestSrcDirNames[0])] + groovyConvention.floatingGroovyTestSrcDirs,
                groovyConvention.groovyTestSrcDirs)
    }
}
