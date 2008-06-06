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
import org.gradle.api.internal.project.DefaultProject
import org.gradle.util.HelperUtil

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
    
    void setUp() {
        super.setUp()
        convention = new JavaPluginConvention(project, [:])
    }

    void testJavaConvention() {
        assert convention.archiveTypes.is(JavaPluginConvention.DEFAULT_ARCHIVE_TYPES)
        assert convention.manifest != null
        assertEquals([], convention.metaInf)
        assertEquals([], convention.floatingSrcDirs)
        assertEquals([], convention.floatingTestSrcDirs)
        assertEquals([], convention.floatingResourceDirs)
        assertEquals([], convention.floatingTestResourceDirs)
        assertEquals('src', convention.srcRootName)
        assertEquals('docs', convention.srcDocsDirName)
        assertEquals('main/webapp', convention.webAppDirName)
        assertEquals('classes', convention.classesDirName)
        assertEquals('test-classes', convention.testClassesDirName)
        assertEquals('distributions', convention.distsDirName)
        assertEquals('docs', convention.docsDirName)
        assertEquals('javadoc', convention.javadocDirName)
        assertEquals('test-results', convention.testResultsDirName)
        assertEquals('reports', convention.reportsDirName)
        assertEquals(['main/java'], convention.srcDirNames)
        assertEquals(['test/java'], convention.testSrcDirNames)
        assertEquals(['main/resources'], convention.resourceDirNames)
        assertEquals(['test/resources'], convention.testResourceDirNames)
    }

    void testDefaultDirs() {
        checkDirs(convention.srcRootName)
    }

    void testDynamicDirs() {
        convention.srcRootName = 'mysrc'
        project.buildDirName = 'mybuild'
        checkDirs(convention.srcRootName)
    }

    private void checkDirs(String srcRootName) {
        convention.floatingSrcDirs << 'someSrcDir' as File
        convention.floatingTestSrcDirs <<'someTestSrcDir' as File
        convention.floatingResourceDirs <<'someResourceDir' as File
        convention.floatingTestResourceDirs <<'someTestResourceDir' as File
        assertEquals(new File(testDir, srcRootName), convention.srcRoot)
        assertEquals([new File(convention.srcRoot, convention.srcDirNames[0])] + convention.floatingSrcDirs, convention.srcDirs)
        assertEquals([new File(convention.srcRoot, convention.testSrcDirNames[0])] + convention.floatingTestSrcDirs, convention.testSrcDirs)
        assertEquals([new File(convention.srcRoot, convention.resourceDirNames[0])] + convention.floatingResourceDirs, convention.resourceDirs)
        assertEquals([new File(convention.srcRoot, convention.testResourceDirNames[0])] + convention.floatingTestResourceDirs, convention.testResourceDirs)
        assertEquals(new File(convention.srcRoot, convention.srcDocsDirName), convention.srcDocsDir)
        assertEquals(new File(convention.srcRoot, convention.webAppDirName), convention.webAppDir)
        assertEquals(new File(project.buildDir, convention.classesDirName), convention.classesDir)
        assertEquals(new File(project.buildDir, convention.testClassesDirName), convention.testClassesDir)
        assertEquals(new File(project.buildDir, convention.distsDirName), convention.distsDir)
        assertEquals(new File(project.buildDir, convention.docsDirName), convention.docsDir)
        assertEquals(new File(project.buildDir, convention.javadocDirName), convention.javadocDir)
        assertEquals(new File(project.buildDir, convention.testResultsDirName), convention.testResultsDir)
        assertEquals(new File(project.buildDir, convention.reportsDirName), convention.reportsDir)
    }


    void testMkdir() {
        String expectedDirName = 'somedir'
        File dir = convention.mkdir(expectedDirName)
        assertEquals(new File(project.buildDir, expectedDirName), dir)
    }

    void testMkdirWithSpecifiedBasedir() {
        String expectedDirName = 'somedir'
        File dir = convention.mkdir(testDir, expectedDirName)
        assertEquals(new File(testDir, expectedDirName), dir)
    }

    void testMkdirWithInvalidArguments() {
        shouldFail(InvalidUserDataException) {
            convention.mkdir(null)
        }
        shouldFail(InvalidUserDataException) {
            convention.mkdir('')
        }
    }
}
