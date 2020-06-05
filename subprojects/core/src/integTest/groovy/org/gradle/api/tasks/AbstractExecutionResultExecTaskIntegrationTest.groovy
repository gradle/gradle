/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractExecutionResultExecTaskIntegrationTest extends AbstractIntegrationSpec {
    protected abstract void makeExecProject()
    protected abstract void writeSucceedingExec()
    protected abstract void writeFailingExec()

    protected String getTaskUnderTestDsl() {
        return "tasks.${taskNameUnderTest}"
    }

    protected abstract String getTaskNameUnderTest()

    protected abstract String getExecResultDsl()

    def "returns null ExecResult when task haven't executed"() {
        makeExecProject()
        writeSucceedingExec()
        buildFile << """
            task verify {
                doLast {
                    assert ${execResultDsl} == null
                }
            }
        """

        when:
        succeeds('verify')

        then:
        result.assertTasksExecutedAndNotSkipped(':verify')
    }

    def "returns ExecResult when is executed"() {
        makeExecProject()
        writeSucceedingExec()
        buildFile << """
            task verify {
                dependsOn(${taskNameUnderTest})
                doLast {
                    assert ${execResultDsl} != null
                    assert ${execResultDsl}.exitValue == 0
                }
            }
        """

        when:
        succeeds('verify')

        then:
        result.assertTasksExecutedAndNotSkipped(':compileJava', ":$taskNameUnderTest", ':verify')
    }

    def "execute with non-zero exit value and ignore exit value should not throw exception"() {
        makeExecProject()
        writeFailingExec()
        buildFile << """
            ${taskUnderTestDsl}.ignoreExitValue = true

            task verify {
                dependsOn(${taskNameUnderTest})
                doLast {
                    assert ${execResultDsl} != null
                    assert ${execResultDsl}.exitValue == 42
                }
            }
        """
        when:
        succeeds('verify')

        then:
        result.assertTasksExecutedAndNotSkipped(':compileJava', ":$taskNameUnderTest", ':verify')
    }
}
