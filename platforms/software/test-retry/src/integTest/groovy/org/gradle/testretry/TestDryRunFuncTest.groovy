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

class TestDryRunFuncTest extends AbstractGeneralPluginFuncTest {

    @Override
    protected String buildConfiguration() {
        return """dependencies {
            testImplementation '${jupiterDependency()}'
            testRuntimeOnly '${junitPlatformLauncherDependency()}'
        }"""
    }

    def "emits skipped test method events if dryRun = true and retry plugin is enabled"() {
        given:
        setupTest(true)
        successfulJUnit5Test()

        when:
        succeeds('test')

        then:
        methodSkipped()
    }

    def "emits skipped test method events when --test-dry-run is used and retry plugin is enabled"() {
        given:
        setupTest(false)
        successfulJUnit5Test()

        when:
        succeeds('test', '--test-dry-run')

        then:
        methodSkipped()
    }

    def "emits skipped test method events, if dryRun is set to true via system properties and retry plugin is enabled"() {
        given:
        setupTest(false, Optional.of(true))
        successfulJUnit5Test()

        when:
        succeeds('test')

        then:
        methodSkipped()
    }

    def "does not emit skipped test method events when --no-test-dry-run is used and retry plugin is enabled"() {
        given:
        setupTest(false)
        successfulJUnit5Test()

        when:
        succeeds('test', '--no-test-dry-run')

        then:
        methodPassed()
    }

    def "does not emit skipped test method events, if dryRun is set to false via system properties and retry plugin is enabled"() {
        given:
        setupTest(false, Optional.of(false))
        successfulJUnit5Test()

        when:
        succeeds('test')

        then:
        methodPassed()
    }

    def "does not emit skipped test method events by default and retry plugin is enabled"() {
        given:
        setupTest(false)
        successfulJUnit5Test()

        when:
        succeeds('test')

        then:
        methodPassed()
    }

    private void setupTest(boolean withTestDryRun) {
        setupTest(withTestDryRun, Optional.empty())
    }

    private void setupTest(boolean withTestDryRun, Optional<Boolean> withSysPropDryRun) {
        buildFile << """
            test {
                useJUnitPlatform()
                ${withTestDryRun ? "dryRun = true" : ""}
                ${withSysPropDryRun.map { it -> it ? "systemProperty('junit.platform.execution.dryRun.enabled', $it)" : "" }.orElse("")}
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

    private boolean methodPassed() {
        return output.count('PASSED') == 1
    }

    private boolean methodSkipped() {
        return output.count('SKIPPED') == 1
    }
}
