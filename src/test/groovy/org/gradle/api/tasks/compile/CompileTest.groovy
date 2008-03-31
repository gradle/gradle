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

package org.gradle.api.tasks.compile

import groovy.mock.interceptor.MockFor
import org.gradle.api.DependencyManager
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.tasks.util.ExistingDirsFilter

/**
 * @author Hans Dockter
 */
class CompileTest extends AbstractConventionTaskTest {
    static final File TEST_TARGET_DIR = '/targetDir' as File
    static final File TEST_ROOT_DIR = '/ROOTDir' as File

    static final List TEST_DEPENDENCY_MANAGER_CLASSPATH = ['jar1' as File]
    static final List TEST_CONVERTED_UNMANAGED_CLASSPATH = ['jar2' as File]
    static final List TEST_UNMANAGED_CLASSPATH = ['jar2']

    Compile compile

    MockFor antCompileMocker

    MockFor dependencyManagerMocker

    void setUp() {
        super.setUp()
        compile = new Compile(project, AbstractTaskTest.TEST_TASK_NAME)
        compile.project.rootDir = TEST_ROOT_DIR
        antCompileMocker = new MockFor(AntJavac)
        dependencyManagerMocker = new MockFor(DependencyManager)
    }

    Task getTask() {
        compile
    }

    void testCompile() {
        assertNotNull(compile.options)
        assertNotNull(compile.existentDirsFilter)
        assertNotNull(compile.classpathConverter)
        assertNull(compile.antCompile)
        assertNull(compile.targetDir)
        assertNull(compile.sourceCompatibility)
        assertNull(compile.targetCompatibility)
        assertNull(compile.dependencyManager)
        assertEquals([], compile.sourceDirs)
        assertEquals([], compile.unmanagedClasspath)
    }

    void testExecute() {
        setUpMocksAndAttributes(compile)
        antCompileMocker.demand.execute(1..1) {List sourceDirs, File targetDir, List classpath, String sourceCompatibility,
                 String targetCompatibility, CompileOptions compileOptions, AntBuilder ant ->
            assertEquals(compile.sourceDirs, sourceDirs)
            assertEquals(TEST_TARGET_DIR, targetDir)
            assertEquals(sourceCompatibility, compile.sourceCompatibility)
            assertEquals(targetCompatibility, compile.targetCompatibility)
            assertEquals(TEST_CONVERTED_UNMANAGED_CLASSPATH + TEST_DEPENDENCY_MANAGER_CLASSPATH, classpath)
            assertEquals(compile.options, compileOptions)
            assert ant.is(compile.project.ant)
        }

        antCompileMocker.use(compile.antCompile) {
            compile.execute()
        }
    }

    void testExecuteWithUnspecifiedTargetDir() {
        setUpMocksAndAttributes(compile)
        compile.targetDir = null
        shouldFailWithCause(InvalidUserDataException) {
            compile.execute()
        }
    }

    void testExecuteWithUnspecifiedSourceCompatibility() {
        setUpMocksAndAttributes(compile)
        compile.sourceCompatibility = null
        shouldFailWithCause(InvalidUserDataException) {
            compile.execute()
        }
    }

    void testExecuteWithUnspecifiedTargetCompatibility() {
        setUpMocksAndAttributes(compile)
        compile.targetCompatibility = null
        shouldFailWithCause(InvalidUserDataException) {
            compile.execute()
        }
    }

    void testExecuteWithUnspecifiedAntCompile() {
        setUpMocksAndAttributes(compile)
        compile.antCompile = null
        shouldFailWithCause(InvalidUserDataException) {
            compile.execute()
        }
    }

    void testExecuteWithNoExisitingSourceDirs() {
        setUpMocksAndAttributes(compile)
        compile.existentDirsFilter = [findExistingDirsAndLogexitMessages: {[]}] as ExistingDirsFilter
        
        antCompileMocker.demand.execute(0..0) {List sourceDirs, File targetDir, List classpath, String sourceCompatibility,
                 String targetCompatibility, CompileOptions compileOptions -> }

        antCompileMocker.use(compile.antCompile) {
            compile.execute()
        }
    }

    void testUnmanagedClasspath() {
        List list1 = ['a', new Object()]
        assert compile.unmanagedClasspath(list1 as Object[]).is(compile)
        assertEquals(list1, compile.unmanagedClasspath)
        List list2 = [['b', 'c']]
        compile.unmanagedClasspath(list2)
        assertEquals(list1 + list2.flatten(), compile.unmanagedClasspath)

    }

    private void setUpMocksAndAttributes(Compile compile) {
        compile.sourceDirs = ['sourceDir1' as File, 'sourceDir2' as File]
        compile.existentDirsFilter = [findExistingDirsAndLogexitMessages: {compile.sourceDirs}] as ExistingDirsFilter
        compile.unmanagedClasspath = TEST_UNMANAGED_CLASSPATH
        compile.sourceCompatibility = "1.5"
        compile.targetCompatibility = '1.5'
        compile.targetDir = TEST_TARGET_DIR
        compile.antCompile = [:] as AntJavac
        compile.dependencyManager = [resolveClasspath: {String taskName ->
            assertEquals(compile.name, taskName)
            TEST_DEPENDENCY_MANAGER_CLASSPATH
        }] as DependencyManager

        compile.classpathConverter = [createFileClasspath: {File baseDir, Object[] pathElements ->
            assertEquals(TEST_ROOT_DIR, baseDir)
            assertEquals(TEST_UNMANAGED_CLASSPATH, pathElements as List)
            TEST_CONVERTED_UNMANAGED_CLASSPATH 
        }] as ClasspathConverter

    }
}
