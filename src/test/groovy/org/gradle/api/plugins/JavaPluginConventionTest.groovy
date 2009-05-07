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
class JavaPluginConventionTest extends AbstractPluginConventionTest {
    private JavaPluginConvention convention

    Class getType() {
        JavaPluginConvention
    }

    Map getCustomValues() {
        [srcRootName: 'newSourceRootName']
    }

    @Before public void setUp() {
        super.setUp()
        project.convention.plugins.reportingBase = new ReportingBasePluginConvention(project)
        convention = new JavaPluginConvention(project, [:])
    }

    @Test public void defaultValues() {
        assert convention.manifest != null
        assertEquals([], convention.metaInf)
        assertEquals([], convention.floatingSrcDirs)
        assertEquals([], convention.floatingTestSrcDirs)
        assertEquals([], convention.floatingResourceDirs)
        assertEquals([], convention.floatingTestResourceDirs)
        assertEquals('src', convention.srcRootName)
        assertEquals('classes', convention.classesDirName)
        assertEquals('test-classes', convention.testClassesDirName)
        assertEquals('distributions', convention.distsDirName)
        assertEquals('', convention.libsDirName)
        assertEquals('docs', convention.docsDirName)
        assertEquals('javadoc', convention.javadocDirName)
        assertEquals('test-results', convention.testResultsDirName)
        assertEquals('tests', convention.testReportDirName)
        assertEquals(['main/java'], convention.srcDirNames)
        assertEquals(['test/java'], convention.testSrcDirNames)
        assertEquals(['main/resources'], convention.resourceDirNames)
        assertEquals(['test/resources'], convention.testResourceDirNames)
        assertEquals(JavaVersion.VERSION_1_5, convention.sourceCompatibility)
        assertEquals(JavaVersion.VERSION_1_5, convention.targetCompatibility)
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
        convention.floatingSrcDirs << 'someSrcDir' as File
        convention.floatingTestSrcDirs << 'someTestSrcDir' as File
        convention.floatingResourceDirs << 'someResourceDir' as File
        convention.floatingTestResourceDirs << 'someTestResourceDir' as File
        assertEquals(new File(testDir, srcRootName), convention.srcRoot)
        assertEquals([new File(convention.srcRoot, convention.srcDirNames[0])] + convention.floatingSrcDirs, convention.srcDirs)
        assertEquals([new File(convention.srcRoot, convention.testSrcDirNames[0])] + convention.floatingTestSrcDirs, convention.testSrcDirs)
        assertEquals([new File(convention.srcRoot, convention.resourceDirNames[0])] + convention.floatingResourceDirs, convention.resourceDirs)
        assertEquals([new File(convention.srcRoot, convention.testResourceDirNames[0])] + convention.floatingTestResourceDirs, convention.testResourceDirs)
        assertEquals(new File(project.buildDir, convention.classesDirName), convention.classesDir)
        assertEquals(new File(project.buildDir, convention.testClassesDirName), convention.testClassesDir)
        assertEquals(new File(project.buildDir, convention.distsDirName), convention.distsDir)
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
