/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.tasks.compile

import groovy.mock.interceptor.MockFor
import org.gradle.api.GradleScriptException
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.compile.ClasspathConverter
import org.gradle.api.tasks.compile.Compile
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*
import org.gradle.api.artifacts.FileCollection

/**
 * @author Hans Dockter
 */
abstract class AbstractCompileTest extends AbstractConventionTaskTest {
    public static final String TEST_PATTERN_1 = 'pattern1'
    public static final String TEST_PATTERN_2 = 'pattern2'
    public static final String TEST_PATTERN_3 = 'pattern3'

    public static final File TEST_TARGET_DIR = '/targetDir' as File
    public static final File TEST_ROOT_DIR = '/ROOTDir' as File

    public static final List TEST_DEPENDENCY_MANAGER_CLASSPATH = ['jar1' as File]
    public static final List TEST_CONVERTED_UNMANAGED_CLASSPATH = ['jar2' as File]
    public static final List TEST_UNMANAGED_CLASSPATH = ['jar2']
    public static final List TEST_INCLUDES = ['incl']
    public static final List TEST_EXCLUDES = ['excl']

    MockFor antCompileMocker

    abstract Compile getCompile()

    @Before public void setUp() {
        super.setUp()
    }

    @Test public void testCompile() {
        assertNotNull(compile.options)
        assertNotNull(compile.existentDirsFilter)
        assertNotNull(compile.classpathConverter)
        assertNotNull(compile.antCompile)
        assertNull(compile.destinationDir)
        assertNull(compile.sourceCompatibility)
        assertNull(compile.targetCompatibility)
        assertNull(compile.srcDirs)
        assertNull(compile.unmanagedClasspath)
    }



    @Test (expected = GradleScriptException) public void testExecuteWithUnspecifiedSourceCompatibility() {
        setUpMocksAndAttributes(compile)
        compile.sourceCompatibility = null
        compile.execute()
    }

    @Test (expected = GradleScriptException) public void testExecuteWithUnspecifiedTargetCompatibility() {
        setUpMocksAndAttributes(compile)
        compile.targetCompatibility = null
        compile.execute()
    }

    @Test (expected = GradleScriptException) public void testExecuteWithUnspecifiedAntCompile() {
        setUpMocksAndAttributes(compile)
        compile.antCompile = null
        compile.execute()
    }

    @Test public void testGetClasspath() {
        setUpMocksAndAttributes(compile)
        assertEquals(TEST_CONVERTED_UNMANAGED_CLASSPATH + TEST_DEPENDENCY_MANAGER_CLASSPATH, compile.getClasspath())
    }

    @Test public void testUnmanagedClasspath() {
        List list1 = ['a', new Object()]
        assert compile.unmanagedClasspath(list1 as Object[]).is(compile)
        assertEquals(list1, compile.unmanagedClasspath)
        List list2 = [['b', 'c']]
        compile.unmanagedClasspath(list2)
        assertEquals(list1 + list2.flatten(), compile.unmanagedClasspath)
    }

    protected void setUpMocksAndAttributes(Compile compile) {
        compile.srcDirs = ['sourceDir1' as File, 'sourceDir2' as File]
        compile.includes = TEST_INCLUDES
        compile.excludes = TEST_EXCLUDES
        compile.existentDirsFilter = [checkDestDirAndFindExistingDirsAndThrowStopActionIfNone: {File destDir, Collection srcDirs ->
            assert destDir.is(compile.destinationDir)
            assert srcDirs.is(compile.srcDirs)
            compile.srcDirs
        }] as ExistingDirsFilter
        compile.unmanagedClasspath = AbstractCompileTest.TEST_UNMANAGED_CLASSPATH
        compile.sourceCompatibility = "1.5"
        compile.targetCompatibility = '1.5'
        compile.destinationDir = AbstractCompileTest.TEST_TARGET_DIR

        compile.configuration = [
            iterator: { -> AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH.iterator()}
        ] as FileCollection

        compile.classpathConverter = [createFileClasspath: {File baseDir, List pathElements ->
            assertEquals(AbstractCompileTest.TEST_ROOT_DIR, baseDir)
            assertEquals(AbstractCompileTest.TEST_UNMANAGED_CLASSPATH, pathElements as List)
            AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH
        }] as ClasspathConverter

    }

    protected ExistingDirsFilter getGroovyCompileExistingDirsFilterMock() {
        return [findExistingDirs: {Collection srcDirs ->
            if (srcDirs.is(compile.srcDirs)) {
                return compile.srcDirs
            } else if (srcDirs.is(compile.groovySourceDirs)) {
                return compile.groovySourceDirs
            }
            fail('srcdirs not passed')

        }] as ExistingDirsFilter
    }

    @Test public void testIncludes() {
        checkIncludesExcludes('include')
    }

    @Test public void testExcludes() {
        checkIncludesExcludes('exclude')
    }

    void checkIncludesExcludes(String name) {
        assert compile."$name"(TEST_PATTERN_1, TEST_PATTERN_2).is(compile)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2], compile."${name}s")
        compile."$name"(TEST_PATTERN_3)
        assertEquals([TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3], compile."${name}s")
    }
}
