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
import org.gradle.api.JavaVersion
import org.gradle.api.internal.project.DefaultProject
import org.gradle.api.internal.tasks.DefaultSourceSetContainer
import org.gradle.util.HelperUtil
import org.junit.Before
import org.junit.Test
import static org.hamcrest.Matchers.*
import static org.junit.Assert.*

/**
 * @author Hans Dockter
 */
class JavaPluginConventionTest {
    private DefaultProject project = HelperUtil.createRootProject()
    private File testDir = project.projectDir
    private JavaPluginConvention convention

    @Before public void setUp() {
        project.convention.plugins.reportingBase = new ReportingBasePluginConvention(project)
        convention = new JavaPluginConvention(project)
    }

    @Test public void defaultValues() {
        assertThat(convention.source, instanceOf(DefaultSourceSetContainer))
        assertThat(convention.manifest, notNullValue())
        assertEquals([], convention.metaInf)
        assertEquals('src', convention.srcRootName)
        assertEquals('dependency-cache', convention.dependencyCacheDirName)
        assertEquals('docs', convention.docsDirName)
        assertEquals('javadoc', convention.javadocDirName)
        assertEquals('test-results', convention.testResultsDirName)
        assertEquals('tests', convention.testReportDirName)
        assertEquals(JavaVersion.VERSION_1_5, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_5, convention.targetCompatibility)
    }

    @Test public void canConfigureSourceSets() {
        File dir = new File('classes-dir')
        convention.source {
            main {
                classesDir = dir
            }
        }
        assertThat(convention.source.main.classesDir, equalTo(dir))
    }
    
    @Test public void testDefaultDirs() {
        checkDirs(convention.srcRootName)
    }

    @Test public void testDynamicDirs() {
        convention.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkDirs(convention.srcRootName)
    }

    private void checkDirs(String srcRootName) {
        assertEquals(new File(testDir, srcRootName), convention.srcRoot)
        assertEquals(new File(project.buildDir, convention.dependencyCacheDirName), convention.dependencyCacheDir)
        assertEquals(new File(project.buildDir, convention.docsDirName), convention.docsDir)
        assertEquals(new File(convention.docsDir, convention.javadocDirName), convention.javadocDir)
        assertEquals(new File(project.buildDir, convention.testResultsDirName), convention.testResultsDir)
        assertEquals(new File(convention.reportsDir, convention.testReportDirName), convention.testReportDir)
    }

    @Test public void testTestReportDirIsCalculatedRelativeToReportsDir() {
        assertEquals(new File(project.buildDir, 'reports/tests'), convention.testReportDir)

        project.reportsDirName = 'other-reports-dir'
        convention.testReportDirName = 'other-test-dir'

        assertEquals(new File(project.buildDir, 'other-reports-dir/other-test-dir'), convention.testReportDir)
    }

    @Test public void testJavadocDirIsCalculatedRelativeToDocsDir() {
        assertEquals(new File(project.buildDir, 'docs/javadoc'), convention.javadocDir)

        convention.docsDirName = 'other-docs-dir'
        convention.javadocDirName = 'other-javadoc-dir'

        assertEquals(new File(project.buildDir, 'other-docs-dir/other-javadoc-dir'), convention.javadocDir)
    }

    @Test public void testMkdir() {
        String expectedDirName = 'somedir'
        File dir = convention.mkdir(expectedDirName)
        assertEquals(new File(project.buildDir, expectedDirName), dir)
    }

    @Test public void testMkdirWithSpecifiedBasedir() {
        String expectedDirName = 'somedir'
        File dir = convention.mkdir(testDir, expectedDirName)
        assertEquals(new File(testDir, expectedDirName), dir)
    }

    @Test (expected = InvalidUserDataException) public void testMkdirWithNullArgument() {
        convention.mkdir(null)
    }

    @Test(expected = InvalidUserDataException) public void testMkdirWithEmptyArguments() {
        convention.mkdir('')
    }

    @Test public void testTargetCompatibilityDefaultsToSourceCompatibilityWhenNotSet() {
        convention.sourceCompatibility = '1.4'
        assertEquals(JavaVersion.VERSION_1_4, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_4, convention.targetCompatibility)

        convention.targetCompatibility = '1.2'
        assertEquals(JavaVersion.VERSION_1_4, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_2, convention.targetCompatibility)

        convention.sourceCompatibility = 6
        assertEquals(JavaVersion.VERSION_1_6, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_2, convention.targetCompatibility)
    }
}
