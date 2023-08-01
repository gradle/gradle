/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/13018")
class SettingsIncludeManyIntegrationTest extends AbstractIntegrationSpec {
    // A list of project paths: project000, project001, ..., project300
    private def projectNames = (0..300).collect {
        "project${it.toString().padLeft(3, "0")}"
    }
    private def projectNamesCommaSeparated = projectNames.collect {
        "\"$it\""
    }.join(", ")

    @Requires(UnitTestPreconditions.IsGroovy3)
    def "including over 250 projects is not possible via varargs in Groovy 3"() {
        // Groovy doesn't even support >=255 args at compilation, so to trigger the right error
        // 254 projects must be used instead.
        settingsFile << """
            rootProject.name = 'root'
            $includeFunction ${projectNames.take(254).collect { "\"$it\"" }.join(", ")}
        """

        expect:
        def result = fails("projects")
        result.assertHasDescription("A problem occurred evaluating settings 'root'.")
        failureCauseContains("org.codehaus.groovy.runtime.ArrayUtil.createArray")

        where:
        includeFunction << ["include", "includeFlat"]
    }

    @Requires(UnitTestPreconditions.IsGroovy4)
    def "including over 250 projects is not possible via varargs in Groovy 4"() {
        // Groovy doesn't even support >=255 args at compilation, so to trigger the right error
        // 254 projects must be used instead.
        settingsFile << """
            rootProject.name = 'root'
            $includeFunction ${projectNames.take(254).collect { "\"$it\"" }.join(", ")}
        """

        expect:
        def result = fails("projects")
        result.assertHasDescription("A problem occurred evaluating settings 'root'.")
        // In Java 8 "call site" is used, in Java 11 "bootstrap method"
        failureHasCause(~/(call site|bootstrap method) initialization exception/)

        where:
        includeFunction << ["include", "includeFlat"]
    }

    @Requires(UnitTestPreconditions.IsGroovy3)
    def "including large amounts of projects is not possible via varargs in Groovy 3"() {
        settingsFile << """
            rootProject.name = 'root'
            $includeFunction $projectNamesCommaSeparated
        """

        // The failure here emits a stacktrace because it's at compilation time
        executer.withStackTraceChecksDisabled()

        expect:
        def result = fails("projects")
        result.assertThatDescription(containsNormalizedString("Could not compile settings file"))
        failureCauseContains("The max number of supported arguments is 255, but found 301")

        where:
        includeFunction << ["include", "includeFlat"]
    }

    @Requires(UnitTestPreconditions.IsGroovy4)
    def "including large amounts of projects is not possible via varargs in Groovy 4"() {
        settingsFile << """
            rootProject.name = 'root'
            $includeFunction $projectNamesCommaSeparated
        """

        // The failure here emits a stacktrace because it's at compilation time
        executer.withStackTraceChecksDisabled()

        expect:
        def result = fails("projects")
        result.assertHasDescription("A problem occurred evaluating settings 'root'.")
        // Java 8 does not print the exception name
        failureHasCause(~/(java.lang.IllegalArgumentException: )?bad parameter count 302/)

        where:
        includeFunction << ["include", "includeFlat"]
    }

    def "including large amounts of projects is possible via a List in Groovy"() {
        settingsFile << """
            rootProject.name = 'root'
            $includeFunction([$projectNamesCommaSeparated])
        """

        when:
        run("projects")

        then:
        for (def name : projectNames) {
            outputContains(name)
        }

        where:
        includeFunction << ["include", "includeFlat"]
    }

    def "including large amounts of projects is possible via varargs in Kotlin"() {
        settingsKotlinFile << """
            rootProject.name = "root"
            $includeFunction($projectNamesCommaSeparated)
        """

        when:
        run("projects")

        then:
        for (def name : projectNames) {
            outputContains(name)
        }

        where:
        includeFunction << ["include", "includeFlat"]
    }

    def "including large amounts of projects is possible via a List in Kotlin"() {
        settingsKotlinFile << """
            rootProject.name = "root"
            $includeFunction(listOf($projectNamesCommaSeparated))
        """

        when:
        run("projects")

        then:
        for (def name : projectNames) {
            outputContains(name)
        }

        where:
        includeFunction << ["include", "includeFlat"]
    }
}
