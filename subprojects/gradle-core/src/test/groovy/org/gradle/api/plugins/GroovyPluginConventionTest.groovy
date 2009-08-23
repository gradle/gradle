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

import static org.junit.Assert.*
import static org.hamcrest.Matchers.*;
import org.junit.Before
import org.junit.Test
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil
import org.gradle.api.file.SourceDirectorySet;

/**
 * @author Hans Dockter
 */
class GroovyPluginConventionTest {
    private DefaultProject project = HelperUtil.createRootProject()
    private File testDir = project.projectDir
    private GroovyPluginConvention groovyConvention
    private JavaPluginConvention javaConvention

    @Before public void setUp()  {
        new JavaPlugin().use(project, project.plugins)
        javaConvention = project.convention.plugins.java
        project.convention.plugins.java = javaConvention
        groovyConvention = new GroovyPluginConvention(project)
    }

    @Test public void testGroovyConvention() {
        assertEquals(['main/groovy'], groovyConvention.groovySrcDirNames)
        assertEquals(['test/groovy'], groovyConvention.groovyTestSrcDirNames)
        assertEquals([], groovyConvention.floatingGroovySrcDirs)
        assertEquals([], groovyConvention.floatingGroovyTestSrcDirs)
    }

    @Test public void testGroovyDefaultDirs() {
        checkGroovyDirs(project.srcRootName)
    }

    @Test public void testGroovyDynamicDirs() {
        project.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkGroovyDirs(project.srcRootName)
    }

    @Test public void testSourceSetsReflectChangesToSourceDirs() {
        SourceDirectorySet src = groovyConvention.groovySrc
        assertThat(src.srcDirs, equalTo(groovyConvention.groovySrcDirs as Set))
        groovyConvention.groovySrcDirNames << 'another'
        assertThat(src.srcDirs, equalTo(groovyConvention.groovySrcDirs as Set))
    }

    @Test public void testTestSourceSetsReflectChangesToTestSourceDirs() {
        SourceDirectorySet src = groovyConvention.groovyTestSrc
        assertThat(src.srcDirs, equalTo(groovyConvention.groovyTestSrcDirs as Set))
        groovyConvention.groovyTestSrcDirNames << 'another'
        assertThat(src.srcDirs, equalTo(groovyConvention.groovyTestSrcDirs as Set))
    }
    
    @Test public void testGroovyDocDirUsesJavaConventionToDetermineDocsDir() {
        assertThat(groovyConvention.groovydocDir, equalTo(new File(javaConvention.docsDir, "groovydoc")))

        groovyConvention.groovydocDirName = "other-dir"
        assertThat(groovyConvention.groovydocDir, equalTo(new File(javaConvention.docsDir, "other-dir")))
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
