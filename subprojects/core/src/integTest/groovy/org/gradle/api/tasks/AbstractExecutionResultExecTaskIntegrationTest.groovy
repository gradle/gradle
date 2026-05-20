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
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

abstract class AbstractExecutionResultExecTaskIntegrationTest extends AbstractIntegrationSpec {
    protected abstract void makeExecProject()
    protected abstract void writeSucceedingExec()
    protected abstract void writeFailingExec()

    def "returns null ExecResult when task haven't executed"() {
        makeExecProject()
        writeSucceedingExec()
        buildFile << verificationTask(false)

        when:
        succeeds('verify')

        then:
        result.assertTasksExecuted(':verify')
        outputContains("Exit value: null")
    }

    @ToBeFixedForConfigurationCache(because = "accessing execResult, https://github.com/gradle/gradle/issues/11492")
    def "returns ExecResult when is executed"() {
        makeExecProject()
        writeSucceedingExec()
        buildFile << verificationTask(true)

        when:
        succeeds('verify')

        then:
        result.assertTasksExecuted(':compileJava', ":run", ':verify')
        outputContains("Exit value: 0")
    }

    def "execute with non-zero exit value should throw exception"() {
        makeExecProject()
        writeFailingExec()
        buildFile << verificationTask(true)

        when:
        fails('verify')

        then:
        failureHasCause(~/Process '.*' finished with non-zero exit value 42/)
    }

    @ToBeFixedForConfigurationCache(because = "accessing execResult, https://github.com/gradle/gradle/issues/11492")
    def "execute with non-zero exit value and ignore exit value should not throw exception"() {
        makeExecProject()
        writeFailingExec()
        buildFile << """
            tasks.run.ignoreExitValue = true
        """
        buildFile << verificationTask(true)

        when:
        succeeds('verify', "--rerun-tasks")

        then:
        result.assertTasksExecuted(':compileJava', ":run", ':verify')
        outputContains("Exit value: 42")
    }

    private static String verificationTask(boolean dependsOn) {
        return """
            abstract class VerifyTask extends DefaultTask {
                @Input
                @Optional
                abstract Property<Integer> getExitValue()

                @TaskAction
                void verify() {
                    println("Exit value: " + exitValue.getOrNull())
                }
            }

            tasks.register("verify", VerifyTask) {
                ${dependsOn ? "dependsOn(run)" : ""}
                exitValue.set(run.flatMap { task -> task.executionResult.map { it.exitValue } })
            }
        """
    }
}
