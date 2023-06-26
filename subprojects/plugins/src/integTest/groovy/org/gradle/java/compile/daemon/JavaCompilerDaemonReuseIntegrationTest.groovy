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
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent
import org.gradle.workers.internal.ExecuteWorkItemBuildOperationType
import org.gradle.workers.internal.KeepAliveMode
import spock.lang.IgnoreIf

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

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons across multiple builds when enabled"() {
        withSingleProjectSources()

        when:
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = compilerDaemonIdentityFile.text.trim()
        assertOneCompilerDaemonIsCreated()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsCreated()

        and:
        compilerDaemonIdentityFile.text.trim() == firstCompilerIdentity
    }

    def "log messages from a compiler daemon are associated with the task that generates them"() {
        def buildOperations = new BuildOperationsFixture(executer, temporaryFolder)

        withSingleProjectSources()
        def classWithWarning = """
            class ClassWithWarning {
                java.util.Date date = new java.util.Date (100, 11, 07);
            }
        """
        file('src/main/java/ClassWithWarning.java') << classWithWarning
        file('src/main2/java/ClassWithWarning.java') << classWithWarning

        when:
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstCompilerIdentity = compilerDaemonIdentityFile.text.trim()
        assertOneCompilerDaemonIsCreated()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        args("-D${KEEP_DAEMON_ALIVE_PROPERTY}=${KeepAliveMode.DAEMON.name()}")
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertOneCompilerDaemonIsCreated()

        and:
        compilerDaemonIdentityFile.text.trim() == firstCompilerIdentity

        and:
        def compilerOperations = buildOperations.all(ExecuteWorkItemBuildOperationType)
        def taskOperations =
            compilerOperations.collect {
                buildOperations.parentsOf(it).reverse().find {parent -> buildOperations.isType(parent, ExecuteTaskBuildOperationType) }
            }

        compilerOperations.size() == 2
        compilerOperations.each {operation ->
            assert operation.progress.find {progress -> progress.details.spans.any { it.text.contains "ClassWithWarning.java uses or overrides a deprecated API" } }
        }
        taskOperations.collect {it.displayName }.containsAll(['Task :compileJava', 'Task :compileMain2Java'])
    }
}
