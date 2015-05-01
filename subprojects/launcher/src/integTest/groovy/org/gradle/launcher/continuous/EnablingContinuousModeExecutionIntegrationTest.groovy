/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.launcher.continuous
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ContinuousBuildTrigger
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.jvm.IncrementalTestJvmComponent
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK7_OR_LATER)
abstract public class EnablingContinuousModeExecutionIntegrationTest extends AbstractIntegrationSpec {
    @Delegate @Rule ContinuousBuildTrigger buildTrigger = new ContinuousBuildTrigger(executer, this)

    abstract IncrementalTestJvmComponent getApp()
    abstract String getCompileTask()
    abstract void validSource()
    abstract void invalidSource()
    abstract void changeSource()
    abstract void createSource()
    abstract void deleteSource()

    List<TestFile> sourceFiles
    List<TestFile> resourceFiles

    GradleHandle getGradle() {
        return buildTrigger.gradle
    }

    def "can enable continuous mode"() {
        when:
        startGradle()
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
    }

    def "warns about incubating feature"() {
        when:
        startGradle()
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        gradle.standardOutput.contains("Continuous mode is an incubating feature.")
    }

    def "prints useful messages when in continuous mode"() {
        when:
        startGradle()
        and:
        afterBuild {
            triggerRebuild()
        }
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        gradle.standardOutput.contains("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.")
        gradle.standardOutput.contains("REBUILD triggered due to test")
        gradle.standardOutput.contains("STOP triggered due to being done")
    }

    def "keeps running even when build fails"() {
        given:
        executer.withStackTraceChecksDisabled()
        invalidSource()

        when:
        startGradle()

        and:
        afterBuild {
            triggerRebuild()
        }

        then:
        buildFailed()

        and:
        afterBuild {
            triggerRebuild()
        }

        then:
        buildFailed()

        and:
        afterBuild {
            triggerStop()
        }

        then:
        waitForStop()
    }

    def "keeps running even when build fails due to script error"() {
        given:
        executer.withStackTraceChecksDisabled()
        buildFile << """
throw new GradleException("config error")
"""
        when:
        startGradle()

        and:
        afterBuild {
            triggerRebuild()
        }

        then:
        buildFailed()

        and:
        afterBuild {
            triggerRebuild()
        }

        then:
        buildFailed()

        and:
        afterBuild {
            triggerStop()
        }

        then:
        waitForStop()
        gradle.errorOutput.count("> config error") == 3
    }

    def "keeps running when build succeeds, fails and succeeds"() {
        given:
        executer.withStackTraceChecksDisabled()
        validSource()

        when:
        startGradle("build")

        and:
        waitForWatching {
            triggerNothing()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        invalidSource()

        and:
        waitForWatching {
            triggerNothing()
        }

        then:
        buildFailed()
        validSource()

        and:
        afterBuild {
            triggerStop()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        waitForStop()
    }

    def "rebuilds when a file changes, is created, or deleted"() {
        given:
        validSource()

        when:
        startGradle("build")

        and:
        waitForWatching {
            triggerNothing()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        changeSource()

        and:
        waitForWatching {
            triggerNothing()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        createSource()

        and:
        waitForWatching {
            triggerNothing()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        deleteSource()

        and:
        afterBuild {
            triggerStop()
        }

        then:
        buildSucceedsAndCompileTaskExecuted()
        waitForStop()
    }

    def buildSucceedsAndCompileTaskExecuted() {
        soFar {
            assertTaskNotSkipped(":${compileTask}")
            assert output.contains("BUILD SUCCESSFUL")
        }
        true
    }

    def buildFailed() {
        soFar {
            assert output.contains("BUILD FAILED")
        }
        true
    }
}
