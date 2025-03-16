/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests


import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@Requires(value = IntegTestPreconditions.NotEmbeddedExecutor, reason = NOT_EMBEDDED_REASON)
class WrapperLoggingIntegrationTest extends AbstractWrapperIntegrationSpec {

    def setup() {
        file("build.gradle") << "task emptyTask"
        executer.beforeExecute {
            withWelcomeMessageEnabled()
        }
    }

    def "wrapper does not render welcome message when executed in quiet mode"() {
        given:
        prepareWrapper()

        when:
        args '-q'
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.empty
    }

    def "wrapper renders welcome message when executed the first time"() {
        given:
        prepareWrapper()

        when:
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        outputContains("Welcome to Gradle $wrapperExecuter.distribution.version.version!")

        when:
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        outputDoesNotContain("Welcome to Gradle $wrapperExecuter.distribution.version.version!")
    }

    def "wrapper renders welcome message when executed the first time after being executed in quiet mode"() {
        given:
        prepareWrapper()

        when:
        args '-q'
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        result.output.empty

        when:
        result = wrapperExecuter.withTasks("emptyTask").run()

        then:
        outputContains("Welcome to Gradle $wrapperExecuter.distribution.version.version!")
    }

    @Requires(UnitTestPreconditions.NotWindows)
    def "wrapper logs and continues when there is a problem setting permissions"() {
        given: "malformed distribution"
        // Repackage distribution with bin/gradle removed so permissions cannot be set
        TestFile tempUnzipDir = temporaryFolder.createDir("temp-unzip")
        distribution.binDistribution.unzipTo(tempUnzipDir)
        assert tempUnzipDir.file("gradle-${distribution.version.baseVersion.version}", "bin", "gradle").delete()
        TestFile tempZipDir = temporaryFolder.createDir("temp-zip-foo")
        TestFile malformedDistZip = new TestFile(tempZipDir, "gradle-${distribution.version.version}-bin.zip")
        tempUnzipDir.zipTo(malformedDistZip)
        prepareWrapper(malformedDistZip.toURI())

        when:
        result = wrapperExecuter
            .withTasks("emptyTask")
            .run()

        then:
        outputContains("Could not set executable permissions")
    }

    def "wrapper prints error and fails build if downloaded zip is empty"() {
        given: "empty distribution"
        TestFile tempUnzipDir = temporaryFolder.createDir("empty-distribution")
        TestFile malformedDistZip = new TestFile(tempUnzipDir, "gradle-${distribution.version.version}-bin.zip") << ""
        prepareWrapper(malformedDistZip.toURI())

        when:
        failure = wrapperExecuter
            .withTasks("emptyTask")
            .withStackTraceChecksDisabled()
            .runWithFailure()

        then:
        failure.assertOutputContains("Could not unzip")
        failure.assertNotOutput("Could not set executable permissions")
    }

    def "wrapper prints progress which contains all tenths of percentages except zero"() {
        given:
        prepareWrapper()

        when:
        result = wrapperExecuter.run()

        then:
        result.getOutputLineThatContains("10%").replaceAll("\\.+", "|") == '|10%|20%|30%|40%|50%|60%|70%|80%|90%|100%'
    }

    @Issue("https://github.com/gradle/gradle/issues/19585")
    def "Can configure log level with command-line Gradle property on Turkish Locale"() {
        setup:
        prepareWrapper()

        expect:
        wrapperExecuter
            .withCommandLineGradleOpts("-Dorg.gradle.logging.level=lifecycle", "-Duser.country=TR", "-Duser.language=tr")
            .withTasks("help")
            .run()
    }
}
