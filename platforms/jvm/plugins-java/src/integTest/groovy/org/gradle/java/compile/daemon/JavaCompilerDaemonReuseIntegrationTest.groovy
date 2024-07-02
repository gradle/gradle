/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.java.compile.daemon

import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.api.problems.internal.FileLocation
import org.gradle.api.tasks.compile.AbstractCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.JavaAgentFixture
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.internal.operations.trace.BuildOperationRecord
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType

import java.util.regex.Pattern

class JavaCompilerDaemonReuseIntegrationTest extends AbstractCompilerDaemonReuseIntegrationTest {
    @Override
    String getCompileTaskType() {
        return "JavaCompile"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "java"
        """
    }

    @Override
    TestJvmComponent getComponent() {
        return new TestJavaComponent()
    }

    @Requires(UnitTestPreconditions.Windows)
    def "compiler daemon is not reused on Windows with Java agent"() {
        withSingleProjectSources()
        def javaAgent = new JavaAgentFixture()
        javaAgent.writeProjectTo(testDirectory)

        buildFile << """
            tasks.compileMain2Java {
                dependsOn("compileJava")
            }
            ${javaAgent.useJavaAgent('compileJava.options.forkOptions')}
        """

        when:
        succeeds("compileAll", "--info")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        outputContains("JavaAgent configured!")
        result.groupedOutput.task(compileTaskPath('main')).assertOutputContains("Worker requested to be persistent, but the JVM argument '-javaagent:${file("javaagent/build/libs/javaagent.jar")}' may make the worker unreliable when reused across multiple builds. Worker will expire at the end of the build session.")
        assertTwoCompilerDaemonsAreRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "compileAll", "--info")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertTwoCompilerDaemonsAreRunning()

        def firstBuild = old(runningCompilerDaemons)
        def secondBuild = runningCompilerDaemons
        def diff = firstBuild - secondBuild
        // We should reuse one daemon from the first build
        diff.size() == 1
    }

    def "reuses compiler daemons across multiple builds when enabled"() {
        withSingleProjectSources()
        buildFile << """
            tasks.compileMain2Java {
                dependsOn("compileJava")
            }
        """

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = old(runningCompilerDaemons[0])
        assertRunningCompilerDaemonIs(firstCompilerIdentity)
    }

    def "log messages from a compiler daemon are associated with the task that generates them"() {
        enableBuildOperationsFixture()
        enableProblemsApiCheck()

        withSingleProjectSources()
        buildFile << """
            tasks.compileMain2Java {
                dependsOn("compileJava")
            }
        """
        def classWithWarning = """
            class ClassWithWarning {
                java.util.Date date = new java.util.Date (100, 11, 07);
            }
        """
        file('src/main/java/ClassWithWarning1.java') << classWithWarning
        file('src/main2/java/ClassWithWarning2.java') << classWithWarning

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = old(runningCompilerDaemons[0])
        assertRunningCompilerDaemonIs(firstCompilerIdentity)

        and:
        def compilerOperations = buildOperationsFixture.all(ExecuteWorkItemBuildOperationType)
        Map<String, BuildOperationRecord> taskOperations =
            compilerOperations.collectEntries {
                def op = buildOperationsFixture.parentsOf(it).reverse().find {
                    parent -> buildOperationsFixture.isType(parent, ExecuteTaskBuildOperationType)
                }
                [op.displayName, it]
            }

        // We expect 4 problems. We need to grab them by calling receivedProblem, otherwise our cleanup check will complain about untested problems
        def problems = (0..3).collect {
            receivedProblem(it)
        }.sort {
            // We sort the problems first by operationId and then by details to make the test deterministic
            // In order to make the sorting less error-prone, we delete the test directory path from the details
            p1, p2 -> return p1.operationId <=> p2.operationId ?: p1.details.replaceAll(Pattern.quote(testDirectory.toString()), '') <=> p2.details.replaceAll(Pattern.quote(testDirectory.toString()), '')
        }

        verifyAll(problems[0]) {
            operationId == taskOperations["Task :compileJava"].id
            fqid == 'compilation:java:java-compilation-note'
            details == "${testDirectory}/src/main/java/ClassWithWarning1.java uses or overrides a deprecated API."
            getSingleLocation(FileLocation).path == "${testDirectory}/src/main/java/ClassWithWarning1.java"
        }
        verifyAll(problems[1]) {
            operationId == taskOperations["Task :compileJava"].id
            fqid == 'compilation:java:java-compilation-note'
            details == 'Recompile with -Xlint:deprecation for details.'
        }
        verifyAll(problems[2]) {
            operationId == taskOperations["Task :compileMain2Java"].id
            fqid == 'compilation:java:java-compilation-note'
            details == "${testDirectory}/src/main2/java/ClassWithWarning2.java uses or overrides a deprecated API."
            getSingleLocation(FileLocation).path == "${testDirectory}/src/main2/java/ClassWithWarning2.java"
        }
        verifyAll(problems[3]) {
            operationId == taskOperations["Task :compileMain2Java"].id
            fqid == 'compilation:java:java-compilation-note'
            details == 'Recompile with -Xlint:deprecation for details.'
        }
    }
}
