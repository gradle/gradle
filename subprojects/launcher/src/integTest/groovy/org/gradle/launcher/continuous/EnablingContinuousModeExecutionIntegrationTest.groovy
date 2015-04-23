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

import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK7_OR_LATER)
public class EnablingContinuousModeExecutionIntegrationTest extends AbstractContinuousModeIntegrationSpec {

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
        buildFile << """
task fail << {
    throw new GradleException("always fails")
}
"""
        when:
        startGradle("fail")
        and:
        afterBuild {
            triggerRebuild()
        }
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
        gradle.standardOutput.count("BUILD FAILED") == 3
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
        gradle.errorOutput.count("config error") == 3
        gradle.standardOutput.count("BUILD FAILED") == 3
    }

    def "keeps running when build succeeds, fails and succeeds"() {
        given:
        executer.withStackTraceChecksDisabled()
        validSource()
        buildFile << """
task maybeFail << {
    def srcFile = file("${srcFile.toURI()}")
    if (srcFile.text != "WORKS") {
        throw new GradleException("always fails")
    }
}
"""
        when:
        startGradle("maybeFail")
        and:
        afterBuild {
            invalidSource()
            triggerRebuild()
        }
        and:
        afterBuild {
            validSource()
            triggerRebuild()
        }
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        gradle.standardOutput.count("BUILD SUCCESSFUL") == 2
        gradle.standardOutput.count("BUILD FAILED") == 1
    }

    def "rebuilds when a file changes, is created, or deleted"() {
        given:
        def outputFile = file("build/output.out")
        validSource()
        buildFile << """
task succeed {
    def output = file("${outputFile.toURI()}")
    inputs.files files("${srcFile.parentFile.toURI()}")
    outputs.files output
    doLast {
        output.parentFile.mkdirs()
        output << "did work"
    }
}
"""
        when:
        startGradle("succeed")
        and:
        afterBuild {
            changeSource()
            triggerNothing()
        }
        and:
        afterBuild {
            createSource()
            triggerNothing()
        }
        and:
        afterBuild {
            deleteSource()
            triggerNothing()
        }
        and:
        afterBuild {
            triggerStop()
        }
        then:
        waitForStop()
        gradle.standardOutput.count("BUILD SUCCESSFUL") == 4
        outputFile.text.count("did work") == 4
    }
}
