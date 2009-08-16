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
package org.gradle.api.plugins.scala

import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.equalTo
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertThat

public class ScalaPluginConventionTest {

    private File testDir
    private DefaultProject project
    private ScalaPluginConvention scalaConvention
    private JavaPluginConvention javaConvention

    @Before public void setUp() {
        project = HelperUtil.createRootProject()
        testDir = project.projectDir
        javaConvention = new JavaPluginConvention(project)
        project.convention.plugins.java = javaConvention
        scalaConvention = new ScalaPluginConvention(project)
    }

    @Test public void testScalaConvention() {
        assertEquals(['main/scala'], scalaConvention.scalaSrcDirNames)
        assertEquals(['test/scala'], scalaConvention.scalaTestSrcDirNames)
        assertEquals([], scalaConvention.floatingScalaSrcDirs)
        assertEquals([], scalaConvention.floatingScalaTestSrcDirs)
    }

    @Test public void testScalaDefaultDirs() {
        checkScalaDirs(project.srcRootName)
    }

    @Test public void testScalaDynamicDirs() {
        project.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkScalaDirs(project.srcRootName)
    }

    @Test public void testScalaDocDirUsesJavaConventionToDetermineDocsDir() {
        assertThat(scalaConvention.scalaDocDir, equalTo(new File(javaConvention.docsDir, "scaladoc")))

        scalaConvention.scalaDocDirName = "other-dir"
        assertThat(scalaConvention.scalaDocDir, equalTo(new File(javaConvention.docsDir, "other-dir")))
    }

    private void checkScalaDirs(String srcRootName) {
        scalaConvention.floatingScalaSrcDirs << 'someScalaSrcDir' as File
        scalaConvention.floatingScalaTestSrcDirs << 'someScalaTestSrcDir' as File
        assertEquals([new File(project.srcRoot, scalaConvention.scalaSrcDirNames[0])] + scalaConvention.floatingScalaSrcDirs,
                scalaConvention.scalaSrcDirs)
        assertEquals([new File(project.srcRoot, scalaConvention.scalaTestSrcDirNames[0])] + scalaConvention.floatingScalaTestSrcDirs,
                scalaConvention.scalaTestSrcDirs)
    }
}
