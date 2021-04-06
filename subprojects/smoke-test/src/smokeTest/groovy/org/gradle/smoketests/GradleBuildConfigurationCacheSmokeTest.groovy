/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.smoketests

import groovy.test.NotYetImplemented
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheMaxProblemsOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheOption
import org.gradle.initialization.StartParameterBuildOptions.ConfigurationCacheProblemsOption
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.Ignore

/**
 * Smoke test building gradle/gradle with configuration cache enabled.
 *
 * gradle/gradle requires Java >=9 and <=11 to build, see {@link AbstractGradleceptionSmokeTest.GradleBuildJvmSpec}.
 */
@Requires(value = TestPrecondition.JDK9_OR_LATER, adhoc = {
    GradleContextualExecuter.isNotConfigCache() && GradleBuildJvmSpec.isAvailable()
})
class GradleBuildConfigurationCacheSmokeTest extends AbstractGradleceptionSmokeTest {

    def "can run Gradle unit tests with configuration cache enabled"() {

        given:
        def supportedTasks = [
            ":tooling-api:publishLocalPublicationToLocalRepository",
            ":base-services:test", "--tests=org.gradle.api.JavaVersionSpec"
        ]

        when:
        configurationCacheRun supportedTasks, 0

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":tooling-api:clean", ":base-services:clean"])

        and:
        configurationCacheRun supportedTasks + ["--info"], 1

        then:
        assertConfigurationCacheStateLoaded()
        result.output.contains("Starting build in new daemon")
        result.task(":tooling-api:publishLocalPublicationToLocalRepository").outcome == TaskOutcome.SUCCESS
    }

    def "can run Gradle integ tests with configuration cache enabled"() {

        given: "tasks whose configuration can only be loaded in the original daemon"
        def supportedTasks = [
            ":configuration-cache:embeddedIntegTest",
            "--tests=org.gradle.configurationcache.ConfigurationCacheDebugLogIntegrationTest"
        ]

        when:
        configurationCacheRun supportedTasks, 0

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":configuration-cache:clean"])

        then:
        configurationCacheRun supportedTasks, 1

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":configuration-cache:embeddedIntegTest").outcome == TaskOutcome.SUCCESS
        assertTestClassExecutedIn "subprojects/configuration-cache", "org.gradle.configurationcache.ConfigurationCacheDebugLogIntegrationTest"
    }

    def "can run Gradle cross-version tests with configuration cache enabled"() {

        given:
        def tasks = [
            ':configuration-cache:embeddedCrossVersionTest',
            '--tests=org.gradle.configurationcache.ConfigurationCacheCrossVersionTest'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":configuration-cache:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":configuration-cache:embeddedCrossVersionTest").outcome == TaskOutcome.SUCCESS
    }

    def "can run Gradle smoke tests with configuration cache enabled"() {

        given:
        def tasks = [
            ':smoke-test:smokeTest',
            '--tests=org.gradle.smoketests.ErrorPronePluginSmokeTest'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":smoke-test:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":smoke-test:smokeTest").outcome == TaskOutcome.SUCCESS
    }

    def "can run Gradle soak tests with configuration cache enabled"() {

        given:
        def tasks = [
            ':soak:forkingIntegTest',
            '--tests=org.gradle.connectivity.MavenCentralDependencyResolveIntegrationTest'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":soak:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":soak:forkingIntegTest").outcome == TaskOutcome.SUCCESS
    }

    @NotYetImplemented
    def "can run Gradle codeQuality with configuration cache enabled"() {

        given:
        def tasks = [':configuration-cache:codeQuality']

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":configuration-cache:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":configuration-cache:runKtlintCheckOverMainSourceSet").outcome == TaskOutcome.SUCCESS
        result.task(":configuration-cache:validatePlugins").outcome == TaskOutcome.SUCCESS
        result.task(":configuration-cache:codenarcIntegTest").outcome == TaskOutcome.SUCCESS
        result.task(":configuration-cache:checkstyleIntegTestGroovy").outcome == TaskOutcome.SUCCESS
        result.task(":configuration-cache:classycleIntegTest").outcome == TaskOutcome.SUCCESS
        result.task(":configuration-cache:codeQuality").outcome == TaskOutcome.SUCCESS
    }

    @NotYetImplemented
    def "can run Gradle checkBinaryCompatibility with configuration cache enabled"() {

        given:
        def tasks = [':architecture-test:checkBinaryCompatibility']

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":architecture-test:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":architecture-test:checkBinaryCompatibility").outcome == TaskOutcome.SUCCESS
    }

    def "can build and install Gradle binary distribution with configuration cache enabled"() {

        given:
        def tasks = [
            ':distributions-full:binDistributionZip',
            ':distributions-full:binInstallation'
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":distributions-full:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":distributions-full:binDistributionZip").outcome == TaskOutcome.SUCCESS
        result.task(":distributions-full:binInstallation").outcome == TaskOutcome.SUCCESS
    }

    @Ignore("Broken by at least the Asciidoctor plugin, and takes 40mins on CI")
    @NotYetImplemented
    def "can build and test Gradle documentation with configuration cache enabled"() {

        given:
        def tasks = [
            ':docs:docs',
            ':docs:docsTest',
            "-D${ConfigurationCacheMaxProblemsOption.PROPERTY_NAME}=8192".toString(), // TODO remove
        ]

        when:
        configurationCacheRun(tasks, 0)

        then:
        assertConfigurationCacheStateStored()

        when:
        run([":docs:clean"])

        then:
        configurationCacheRun(tasks, 1)

        then:
        assertConfigurationCacheStateLoaded()
        result.task(":docs:docs").outcome == TaskOutcome.SUCCESS
        result.task("':docs:docsTest'").outcome == TaskOutcome.SUCCESS
    }

    @Override
    protected void assertConfigurationCacheStateStored() {
        assert result.output.count("Calculating task graph as no configuration cache is available") == 1
    }

    @Override
    protected void assertConfigurationCacheStateLoaded() {
        assert result.output.count("Reusing configuration cache") == 1
    }

    private TestExecutionResult assertTestClassExecutedIn(String subProjectDir, String testClass) {
        new DefaultTestExecutionResult(file(subProjectDir), "build", "", "", "embeddedIntegTest")
            .assertTestClassesExecuted(testClass)
    }

    private void configurationCacheRun(List<String> tasks, int daemonId = 0) {
        run(
            tasks + [
                "--${ConfigurationCacheOption.LONG_OPTION}".toString(),
                "--${ConfigurationCacheProblemsOption.LONG_OPTION}=warn".toString(), // TODO remove
                TEST_BUILD_TIMESTAMP
            ],
            // use a unique testKitDir per daemonId other than 0 as 0 means default daemon.
            daemonId != 0 ? file("test-kit/$daemonId") : null
        )
    }
}



