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
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractTaskTest
import org.gradle.api.DependencyManager
import org.gradle.api.tasks.util.ExistingDirsFilter
import org.gradle.util.GradleUtil

/**
 * @author Hans Dockter
 */
class GroovyCompileTest extends AbstractCompileTest {
    static final List TEST_GROOVY_CLASSPATH = ['groovy.jar']

    GroovyCompile testObj

    MockFor antJavacCompileMocker
    MockFor antGroovycCompileMocker

    MockFor dependencyManagerMocker

    Compile getCompile() {
        testObj
    }

    void setUp() {
        super.setUp()
        testObj = new GroovyCompile(project, AbstractTaskTest.TEST_TASK_NAME)
        testObj.project.rootDir = AbstractCompileTest.TEST_ROOT_DIR
        antJavacCompileMocker = new MockFor(AntJavac)
        antGroovycCompileMocker = new MockFor(AntGroovyc)
        dependencyManagerMocker = new MockFor(DependencyManager)
    }

    Task getTask() {
        testObj
    }

    void testExecute() {
        setUpMocksAndAttributes(testObj)
        antJavacCompileMocker.demand.execute(1..1) {List sourceDirs, List includes, List excludes, File targetDir, List classpath, String sourceCompatibility,
                                                    String targetCompatibility, CompileOptions compileOptions, AntBuilder ant ->
            assertEquals(testObj.srcDirs, sourceDirs)
            assertEquals(AbstractCompileTest.TEST_TARGET_DIR, targetDir)
            assertEquals(sourceCompatibility, testObj.sourceCompatibility)
            assertEquals(targetCompatibility, testObj.targetCompatibility)
            assertEquals(testObj.includes, includes)
            assertEquals(testObj.excludes, excludes)
            assertEquals(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH + AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH,
                    classpath)
            assertEquals(testObj.options, compileOptions)
            assert ant.is(testObj.project.ant)
        }
        antGroovycCompileMocker.demand.execute(1..1) {AntBuilder ant, List sourceDirs, List groovyIncludes, List groovyExcludes,
                                                      List groovyJavaIncludes, List groovyJavaExcludes,
                                                      File targetDir, List classpath, String sourceCompatibility,
                                                      String targetCompatibility, CompileOptions compileOptions, List taskClasspath ->
            assertEquals(testObj.groovySourceDirs, sourceDirs)
            assertEquals(testObj.groovyIncludes, groovyIncludes)
            assertEquals(testObj.groovyExcludes, groovyExcludes)
            assertEquals(testObj.groovyJavaIncludes, groovyJavaIncludes)
            assertEquals(testObj.groovyJavaExcludes, groovyJavaExcludes)
            assertEquals(AbstractCompileTest.TEST_TARGET_DIR, targetDir)
            assertEquals(sourceCompatibility, testObj.sourceCompatibility)
            assertEquals(targetCompatibility, testObj.targetCompatibility)
            assertEquals(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH + AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH,
                    classpath)
            assertEquals(GradleUtil.antJarFiles + TEST_GROOVY_CLASSPATH, taskClasspath)
            assertEquals(testObj.options, compileOptions)
            assert ant.is(testObj.project.ant)

        }
        antGroovycCompileMocker.use(testObj.antGroovyCompile) {
            antJavacCompileMocker.use(compile.antCompile) {
                testObj.execute()
            }
        }
    }

    void setUpMocksAndAttributes(GroovyCompile compile) {
        super.setUpMocksAndAttributes((Compile) compile)
        compile.groovyClasspath = TEST_GROOVY_CLASSPATH
        compile.groovySourceDirs = ['groovySourceDir1' as File, 'groovySourceDir2' as File]
        compile.existentDirsFilter = [findExistingDirs: {Collection srcDirs ->
            if (srcDirs.is(compile.srcDirs)) {
                return compile.srcDirs
            } else if (srcDirs.is(compile.groovySourceDirs)) {
                return compile.groovySourceDirs
            }
            fail('srcdirs not passed')

        }] as ExistingDirsFilter
    }

    void testGroovyIncludes() {
        checkIncludesExcludes('groovyInclude')
    }

    void testGroovyExcludes() {
        checkIncludesExcludes('groovyExclude')
    }
}
