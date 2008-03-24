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
class JavaConventionTest extends GroovyTestCase {
    DefaultProject project
    File testDir
    JavaConvention convention

    void setUp() {
        testDir = HelperUtil.makeNewTestDir()
        project = [getProjectDir: {testDir}] as DefaultProject
        convention = new JavaConvention(project)
    }

    void testJavaConvention() {
        assert convention.archiveTypes.is(JavaConvention.DEFAULT_ARCHIVE_TYPES)
        assert convention.manifest != null
        assert convention.metaInf != null
        assertEquals(new File(testDir, 'src'), convention.srcRoot)
        assertEquals(new File(testDir, 'build'), convention.buildDir)
        assertEquals(new File(convention.buildDir, 'classes'), convention.classesDir)
        assertEquals(new File(convention.buildDir, 'test-classes'), convention.testClassesDir)
        assertEquals(new File(convention.buildDir, 'test-results'), convention.testResultsDir)
        assertEquals([new File(convention.srcRoot, 'main/java')], convention.srcDirs)
        assertEquals([new File(convention.srcRoot, 'test/java')], convention.testSrcDirs)
        assertEquals([new File(convention.srcRoot, 'main/resources')], convention.resourceDirs)
        assertEquals([new File(convention.srcRoot, 'test/resources')], convention.testResourceDirs)
    }

    void testMkdir() {
        String expectedDirName = 'somedir' 
        File dir = convention.mkdir(expectedDirName)
        assertEquals(new File(convention.buildDir, expectedDirName), dir)
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
