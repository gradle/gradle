/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testkit.scenario

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testkit.runner.GradleRunner
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.util.TextUtil.normaliseFileSeparators


class GradleScenarioTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def "reasonable error message when no runner factory provided"() {

        when:
        GradleScenario.create().run()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No Gradle runner factory provided. Use withRunnerFactory(Supplier<GradleRunner>)"
    }

    def "reasonable error message when no base directory provided"() {

        when:
        GradleScenario.create()
            .withRunnerFactory { GradleRunner.create() }
            .run()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No base directory provided. Use withBaseDirectory(File)"
    }

    def "reasonable error message when provided base directory is an existing file"() {

        given:
        def baseDir = testDirectoryProvider.testDirectory.file("some")
        baseDir.text = ""

        when:
        GradleScenario.create()
            .withRunnerFactory { GradleRunner.create() }
            .withBaseDirectory(baseDir)
            .run()

        then:
        def ex = thrown(IllegalArgumentException)
        normaliseFileSeparators(ex.message) == "Provided base directory '$baseDir' exists and is a file"
    }

    def "reasonable error message when provided base directory is an existing non-empty directory"() {

        given:
        def baseDir = testDirectoryProvider.testDirectory.file("some")
        baseDir.mkdirs()
        baseDir.file("content").text = ""

        when:
        GradleScenario.create()
            .withRunnerFactory { GradleRunner.create() }
            .withBaseDirectory(baseDir)
            .run()

        then:
        def ex = thrown(IllegalArgumentException)
        normaliseFileSeparators(ex.message) == "Provided base directory '$baseDir' is a non-empty directory"
    }

    def "reasonable error message when no steps provided"() {

        when:
        GradleScenario.create()
            .withRunnerFactory { GradleRunner.create() }
            .withBaseDirectory(testDirectoryProvider.testDirectory.file("some"))
            .run()

        then:
        def ex = thrown(IllegalStateException)
        ex.message == "No scenario steps provided. Use withSteps {}"
    }
}
