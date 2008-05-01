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

/**
 * @author Hans Dockter
 */
class GroovyConventionTest extends JavaConventionTest {
    private GroovyConvention groovyConvention

    JavaConvention getConvention() {
        if (!groovyConvention) {
            groovyConvention = new GroovyConvention(project)
        }
        groovyConvention
    }

    void setUp() {
        super.setUp()
        groovyConvention = getConvention()
    }

    void testGroovyConvention() {
        assertEquals(['main/groovy'], groovyConvention.groovySrcDirNames)
        assertEquals(['test/groovy'], groovyConvention.groovyTestSrcDirNames)
        assertEquals([], groovyConvention.floatingGroovySrcDirs)
        assertEquals([], groovyConvention.floatingGroovyTestSrcDirs)
    }

    void testGroovyDefaultDirs() {
        checkGroovyDirs(convention.srcRootName)
    }

    void testGroovyDynamicDirs() {
        convention.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkGroovyDirs(convention.srcRootName)
    }

    private void checkGroovyDirs(String srcRootName) {
        groovyConvention.floatingGroovySrcDirs << 'someGroovySrcDir' as File
        groovyConvention.floatingGroovyTestSrcDirs <<'someGroovyTestSrcDir' as File
        assertEquals([new File(convention.srcRoot, groovyConvention.groovySrcDirNames[0])] + groovyConvention.floatingGroovySrcDirs,
                groovyConvention.groovySrcDirs)
        assertEquals([new File(convention.srcRoot, groovyConvention.groovyTestSrcDirNames[0])] + groovyConvention.floatingGroovyTestSrcDirs,
                groovyConvention.groovyTestSrcDirs)
    }
}
