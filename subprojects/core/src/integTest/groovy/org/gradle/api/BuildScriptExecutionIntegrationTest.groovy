/*
 * Copyright 2010 the original author or authors.
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


package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

@SuppressWarnings("IntegrationTestFixtures")
class BuildScriptExecutionIntegrationTest extends AbstractIntegrationTest {

    @Test
    void executesBuildScriptWithCorrectEnvironment() {
        def implClassName = 'com.google.common.collect.Multimap'
        TestFile buildScript = testFile('build.gradle')
        buildScript << """
println 'quiet message'
logging.captureStandardOutput(LogLevel.ERROR)
println 'error message'
assert project != null
assert "${buildScript.absolutePath.replace("\\", "\\\\")}" == buildscript.sourceFile as String
assert "${buildScript.toURI()}" == buildscript.sourceURI as String
assert buildscript.classLoader == getClass().classLoader.parent
assert buildscript.classLoader == Thread.currentThread().contextClassLoader
Gradle.class.classLoader.loadClass('${implClassName}')
try {
    buildscript.classLoader.loadClass('${implClassName}')
    assert false: 'should fail'
} catch (ClassNotFoundException e) {
    // expected
}

            task doStuff
"""

        ExecutionResult result = inTestDirectory().withTasks('doStuff').run()
        result.assertOutputContains('quiet message')
        result.assertHasErrorOutput('error message')
    }

    @Test
    void buildScriptCanContainATaskDefinition() {

        testFile('build.gradle') << '''
            task t(type: SomeTask)

            class SomeTask extends DefaultTask {
            }
'''

        inTestDirectory().withTasks("help").run()
    }

    @Test
    void buildScriptCanContainOnlyClassDefinitions() {

        testFile('build.gradle') << '''
            class TestComparable implements Comparable<TestComparable>, SomeInterface {
                int compareTo(TestComparable t) {
                    return 0
                }
                void main() { }
            }

            interface SomeInterface {
                void main()
            }
'''

        inTestDirectory().withTasks("help").run()
    }
}
