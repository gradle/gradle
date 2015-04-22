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

import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf

@IgnoreIf({!TestPrecondition.JDK7_OR_LATER})
public class EnablingContinuousModeExecutionIntegrationTest extends AbstractContinuousModeIntegrationSpec {
    def gradle
    def srcFile = file("src.file")

    void startGradle(String task="tasks") {
        gradle = executer.withTasks(task).start()
    }

    private void waitForStop() {
        gradle.waitForFinish()
    }

    private void waitForBuildFinished() {
        server.sync()
    }

    def afterBuild(Closure c) {
        server.waitFor()
        c.call()
        server.release()
    }

    def "can enable continuous mode"() {
        when:
        goingToStop()
        and:
        startGradle()
        then:
        waitForBuildFinished()
        waitForStop()
    }

    def "warns about incubating feature"() {
        when:
        goingToStop()
        and:
        startGradle()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.standardOutput.contains("Continuous mode is an incubating feature.")
    }

    def "prints useful messages when in continuous mode"() {
        when:
        goingToRebuild()
        and:
        startGradle()
        then:
        waitForBuildFinished()
        when:
        goingToStop()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.standardOutput.contains("Waiting for a trigger. To exit 'continuous mode', use Ctrl+C.")
        gradle.standardOutput.contains("REBUILD triggered due to test")
        gradle.standardOutput.contains("STOP triggered due to being done")
    }

    def "keeps running even when build fails"() {
        given:
        buildFile << """
task fail << {
    throw new GradleException("always fails")
}
"""
        when:
        goingToRebuild()
        and:
        startGradle("fail")
        then:
        waitForBuildFinished()
        when:
        goingToRebuild()
        then:
        waitForBuildFinished()
        when:
        goingToStop()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.standardOutput.count("BUILD FAILED") == 3
    }

    def "keeps running even when build fails due to script error"() {
        given:
        buildFile << """
throw new GradleException("config error")
"""
        when:
        goingToRebuild()
        and:
        startGradle()
        then:
        waitForBuildFinished()
        when:
        goingToRebuild()
        then:
        waitForBuildFinished()
        when:
        goingToStop()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.errorOutput.count("config error") == 3
        gradle.standardOutput.count("BUILD FAILED") == 3
    }

    def validSource() {
        srcFile.text = "WORKS"
    }
    def invalidSource() {
        srcFile.text = "BROKEN"
    }
    def changeSource() {
        srcFile << "NEWLINE"
    }

    def "keeps running when build succeeds, fails and succeeds"() {
        given:
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
        goingToRebuild()
        and:
        startGradle("maybeFail")
        and:
        afterBuild {
            invalidSource()
        }
        and:
        goingToRebuild()
        and:
        afterBuild {
            validSource()
        }
        and:
        goingToStop()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.standardOutput.count("BUILD SUCCESSFUL") == 2
        gradle.standardOutput.count("BUILD FAILED") == 1
    }

    def "rebuilds when a file changes"() {
        given:
        def outputFile = file("build/output.out")
        validSource()
        buildFile << """
task succeed {
    def output = file("${outputFile.toURI()}")
    inputs.files file("${srcFile.toURI()}")
    outputs.files output
    doLast {
        output.parentFile.mkdirs()
        output << "did work"
    }
}
"""
        when:
        goingToWait()
        and:
        startGradle("succeed")
        and:
        afterBuild {
            changeSource()
        }
        and:
        goingToWait()
        and:
        afterBuild {
            changeSource()
        }
        and:
        goingToStop()
        then:
        waitForBuildFinished()
        waitForStop()
        gradle.standardOutput.count("BUILD SUCCESSFUL") == 3
        outputFile.text.count("did work") == 3
    }
}
