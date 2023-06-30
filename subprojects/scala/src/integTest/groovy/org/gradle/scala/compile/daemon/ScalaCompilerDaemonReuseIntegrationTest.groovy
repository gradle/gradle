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

package org.gradle.scala.compile.daemon

import org.gradle.api.tasks.compile.AbstractCompilerDaemonReuseIntegrationTest
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.scala.fixtures.TestScalaComponent
import org.gradle.test.fixtures.file.TestFile
import org.junit.Assume
import spock.lang.IgnoreIf


class ScalaCompilerDaemonReuseIntegrationTest extends AbstractCompilerDaemonReuseIntegrationTest {
    @Override
    String getCompileTaskType() {
        return "ScalaCompile"
    }

    @Override
    String getApplyAndConfigure() {
        return """
            apply plugin: "scala"

            ${mavenCentralRepository()}

            dependencies {
                implementation 'org.scala-lang:scala-library:2.11.12'
            }
        """
    }

    @Override
    TestJvmComponent getComponent() {
        return new TestScalaComponent()
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a single project across multiple builds when enabled"() {
        withSingleProjectSources()
        withPersistentScalaCompilerDaemons()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstDaemonId = assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertRunningCompilerDaemonIs(firstDaemonId)
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "reuses compiler daemons within a multi-project build across multiple builds when enabled"() {
        withMultiProjectSources()
        withPersistentScalaCompilerDaemons()

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        def firstDaemonId = assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertRunningCompilerDaemonIs(firstDaemonId)
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    def "reuses compiler daemons within a composite build across multiple builds when enabled"() {
        Assume.assumeTrue(supportsCompositeBuilds())

        withCompositeBuildSources()
        withPersistentScalaCompilerDaemons()
        withPersistentScalaCompilerDaemons(file('child'))

        when:
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        def firstDaemonId = assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        succeeds("clean", "child:clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", ":child${compileTaskPath('main')}"

        and:
        assertRunningCompilerDaemonIs(firstDaemonId)
    }

    @IgnoreIf({ GradleContextualExecuter.parallel })
    @UnsupportedWithConfigurationCache(because = "parallel by default")
    def "ignores known changing environment variable when persistent compiler daemons are enabled"() {
        withSingleProjectSources()
        withPersistentScalaCompilerDaemons()

        when:
        executer.withEnvironmentVars(['JAVA_MAIN_CLASS_1234': '1234'])
        succeeds("compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        def firstDaemonId = assertOneCompilerDaemonIsRunning()

        when:
        executer.withWorkerDaemonsExpirationDisabled()
        executer.withEnvironmentVars(['JAVA_MAIN_CLASS_1234': '5678'])
        succeeds("clean", "compileAll")

        then:
        executedAndNotSkipped "${compileTaskPath('main')}", "${compileTaskPath('main2')}"

        and:
        assertRunningCompilerDaemonIs(firstDaemonId)
    }

    private TestFile withPersistentScalaCompilerDaemons(TestFile buildDir = testDirectory) {
        buildDir.file("build.gradle") << """
            allprojects {
                tasks.withType(${compileTaskType}) {
                    scalaCompileOptions.keepAliveMode = KeepAliveMode.DAEMON
                }
            }
        """
    }
}
