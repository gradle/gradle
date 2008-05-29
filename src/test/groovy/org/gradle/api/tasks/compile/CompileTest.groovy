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
import org.gradle.api.Task
import org.gradle.api.tasks.AbstractTaskTest

/**
 * @author Hans Dockter
 */
class CompileTest extends AbstractCompileTest {
    Compile compile

    MockFor antCompileMocker

    MockFor dependencyManagerMocker

    void setUp() {
        super.setUp()
        compile = new Compile(project, AbstractTaskTest.TEST_TASK_NAME)
        compile.project.rootDir = AbstractCompileTest.TEST_ROOT_DIR
        antCompileMocker = new MockFor(AntJavac)
        dependencyManagerMocker = new MockFor(DependencyManager)
    }
           
    Task getTask() {
        compile
    }

    void testExecute() {
        setUpMocksAndAttributes(compile)
        antCompileMocker.demand.execute(1..1) {List sourceDirs, List includes, List excludes, File targetDir, List classpath, String sourceCompatibility,
                                               String targetCompatibility, CompileOptions compileOptions, AntBuilder ant ->
            assertEquals(compile.srcDirs, sourceDirs)
            assertEquals(compile.includes, includes)
            assertEquals(compile.excludes, excludes)
            assertEquals(compile.srcDirs, sourceDirs)
            assertEquals(AbstractCompileTest.TEST_TARGET_DIR, targetDir)
            assertEquals(sourceCompatibility, compile.sourceCompatibility)
            assertEquals(targetCompatibility, compile.targetCompatibility)
            assertEquals(AbstractCompileTest.TEST_CONVERTED_UNMANAGED_CLASSPATH + AbstractCompileTest.TEST_DEPENDENCY_MANAGER_CLASSPATH,
                    classpath)
            assertEquals(compile.options, compileOptions)
            assert ant.is(compile.project.ant)
        }

        antCompileMocker.use(compile.antCompile) {
            compile.execute()
        }
    }

    // todo We need to do this to make the compiler happy. We need to file a Jira to Groovy.
    Compile getCompile() {
        compile
    }
}
