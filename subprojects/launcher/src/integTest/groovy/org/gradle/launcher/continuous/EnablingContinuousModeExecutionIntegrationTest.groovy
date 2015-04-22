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
    def "can enable continuous mode"() {
        when:
        planToStop()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        gradle.waitForFinish()
    }

    def "warns about incubating feature"() {
        when:
        planToStop()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.contains("Continuous mode is an incubating feature.")
    }

    def "prints useful messages when in continuous mode"() {
        when:
        planToRebuild()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        when:
        planToStop()
        then:
        server.sync()
        gradle.waitForFinish()
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
        planToRebuild()
        and:
        def gradle = executer.withTasks("fail").start()
        then:
        server.sync()
        when:
        planToRebuild()
        then:
        server.sync()
        when:
        planToStop()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.count("BUILD FAILED") == 3
    }

    def "keeps running even when build fails due to script error"() {
        given:
        buildFile << """
throw new GradleException("config error")
"""
        when:
        planToRebuild()
        and:
        def gradle = executer.withTasks("tasks").start()
        then:
        server.sync()
        when:
        planToRebuild()
        then:
        server.sync()
        when:
        planToStop()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.count("BUILD FAILED") == 3
    }

    def "keeps running when build succeeds, fails and succeeds"() {
        given:
        def srcFile = file("src.file")
        srcFile.text = "WORKS"
        buildFile << """
task maybeFail << {
    def srcFile = file("${srcFile.toURI()}")
    if (srcFile.text != "WORKS") {
        throw new GradleException("always fails")
    }
}
"""
        when:
        planToRebuild()
        and:
        def gradle = executer.withTasks("maybeFail").start()
        then:
        server.waitFor()
        when:
        srcFile.text = "BROKEN"
        server.release()
        planToRebuild()
        then:
        server.waitFor()
        when:
        srcFile.text = "WORKS"
        server.release()
        planToStop()
        then:
        server.sync()
        gradle.waitForFinish()
        gradle.standardOutput.count("BUILD SUCCESSFUL") == 2
        gradle.standardOutput.count("BUILD FAILED") == 1
    }
}
