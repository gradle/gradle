/*
 * Copyright 2023 the original author or authors.
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
package org.gradle.testretry

import org.gradle.testkit.runner.BuildResult
import org.gradle.util.GradleVersion

class TestDryRunFuncTest extends AbstractGeneralPluginFuncTest {

    private static final GradleVersion GRADLE_8_3 = GradleVersion.version("8.3")

    @Override
    protected String buildConfiguration() {
        return """dependencies {
            testImplementation '${jupiterDependency()}'
            testRuntimeOnly '${junitPlatformLauncherDependency()}'
        }"""
    }

    def "emits skipped test method events if dryRun = true and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, true)
        successfulJUnit5Test()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        gradle83OrAbove ? methodSkipped(result) : methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "emits skipped test method events when --test-dry-run is used and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, false)
        successfulJUnit5Test()

        when:
        def result = gradle83OrAbove ? gradleRunner(gradleVersion,  'test', '-S', "--test-dry-run").build() : gradleRunner(gradleVersion).build()

        then:
        gradle83OrAbove ? methodSkipped(result) : methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "emits skipped test method events, if dryRun is set to true via system properties and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, false, Optional.of(true))
        successfulJUnit5Test()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        gradle83OrAbove ? methodSkipped(result) : methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not emit skipped test method events when --no-test-dry-run is used and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, false)
        successfulJUnit5Test()

        when:
        def result = gradle83OrAbove ? gradleRunner(gradleVersion,  'test', '-S', "--no-test-dry-run").build() : gradleRunner(gradleVersion).build()

        then:
        methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not emit skipped test method events, if dryRun is set to false via system properties and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, false, Optional.of(false))
        successfulJUnit5Test()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    def "does not emit skipped test method events by default and retry plugin is enabled"() {
        given:
        def gradle83OrAbove = GradleVersion.version(gradleVersion) >= GRADLE_8_3
        setupTest(gradle83OrAbove, false)
        successfulJUnit5Test()

        when:
        def result = gradleRunner(gradleVersion).build()

        then:
        methodPassed(result)

        where:
        gradleVersion << GRADLE_VERSIONS_UNDER_TEST
    }

    private void setupTest(boolean gradle83OrAbove, boolean withTestDryRun) {
        setupTest(gradle83OrAbove, withTestDryRun, Optional.empty())
    }

    private void setupTest(boolean gradle83OrAbove, boolean withTestDryRun, Optional<Boolean> withSysPropDryRun) {
        buildFile << """
            test {
                useJUnitPlatform()
                ${gradle83OrAbove && withTestDryRun ? "dryRun = true" : ""}
                ${withSysPropDryRun.map { it -> gradle83OrAbove && it ? "systemProperty('junit.platform.execution.dryRun.enabled', $it)" : "" }.orElse("")}
                retry {
                    maxRetries = 1
                }
            }
        """
    }

    private void successfulJUnit5Test() {
        writeJavaTestSource """
            package acme;

            public class SuccessfulTests {
                @org.junit.jupiter.api.Test
                public void successTest() {}
            }
        """
    }

    private static boolean methodPassed(BuildResult result) {
        return result.output.count('PASSED') == 1
    }

    private static boolean methodSkipped(BuildResult result) {
        return result.output.count('SKIPPED') == 1
    }
}
