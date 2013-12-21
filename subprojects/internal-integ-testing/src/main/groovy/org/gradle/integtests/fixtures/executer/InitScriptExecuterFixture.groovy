/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.junit.rules.MethodRule
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

abstract class InitScriptExecuterFixture implements MethodRule {

    GradleExecuter executer
    TestDirectoryProvider testDir

    InitScriptExecuterFixture(GradleExecuter executer, TestDirectoryProvider testDir) {
        this.executer = executer
        this.testDir = testDir
    }

    abstract String initScriptContent()

    abstract void afterBuild()

    Statement apply(Statement base, FrameworkMethod method, Object target) {
        def temporaryFolder = testDir.testDirectory
        def initFile = temporaryFolder.file(this.getClass().getSimpleName() + "-init.gradle")
        initFile.text = initScriptContent()
        executer.beforeExecute {
            usingInitScript(initFile)
        }
        executer.afterExecute {
            afterBuild()
        }

        return new Statement() {
            public void evaluate() throws Throwable {
                base.evaluate();
            }
        }
    }
}
