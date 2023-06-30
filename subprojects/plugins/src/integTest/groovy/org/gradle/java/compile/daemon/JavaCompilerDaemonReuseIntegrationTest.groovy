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
import org.gradle.api.tasks.compile.AbstractCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType
import org.gradle.workers.internal.KeepAliveMode

import static org.gradle.api.internal.tasks.compile.DaemonJavaCompiler.KEEP_DAEMON_ALIVE_PROPERTY

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

    def "reuses compiler daemons across multiple builds when enabled"() {
        withSingleProjectSources()
        buildFile << """
            tasks.compileMain2Java {
                dependsOn("compileJava")
            }
        """

        when:
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = compilerDaemonIdentityFile.text.trim()
        assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsRunning()

        and:
        compilerDaemonIdentityFile.text.trim() == firstCompilerIdentity
    }

    def "log messages from a compiler daemon are associated with the task that generates them"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

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
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = compilerDaemonIdentityFile.text.trim()
        assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsRunning()

        and:
        compilerDaemonIdentityFile.text.trim() == firstCompilerIdentity

        and:
        def compilerOperations = buildOperations.all(ExecuteWorkItemBuildOperationType)
        def taskOperations =
            compilerOperations.collectEntries {
                def op = buildOperations.parentsOf(it).reverse().find {parent -> buildOperations.isType(parent, ExecuteTaskBuildOperationType) }
                [op.displayName, it]
            }

        def tasks = ['Task :compileJava', 'Task :compileMain2Java']
        taskOperations.keySet() == tasks.toSet()
        tasks.eachWithIndex { taskName, index ->
            def operation = taskOperations[taskName]
            assert operation.progress.find {progress -> progress.details.spans.any { it.text.contains "ClassWithWarning${index + 1}.java uses or overrides a deprecated API" } }
        }
    }
}
