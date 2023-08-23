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

package org.gradle.testing.junit.jupiter

import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.junit.AbstractJUnitConsoleLoggingIntegrationTest
import org.gradle.util.internal.VersionNumber
import org.junit.Assume

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_JUPITER

@TargetCoverage({ JUNIT_JUPITER })
class JUnitJupiterConsoleLoggingIntegrationTest extends AbstractJUnitConsoleLoggingIntegrationTest implements JUnitJupiterMultiVersionTest {
    @Override
    String getMaybePackagePrefix() {
        return ''
    }

    def "failure during JUnit platform initialization is written to console when granularity is set"() {
        Assume.assumeTrue("Non-existent test engine is only an error after 5.9.0", VersionNumber.parse(version) >= VersionNumber.parse("5.9.0"))

        buildFile.text = """
            apply plugin: "groovy"

            ${mavenCentralRepository()}

            dependencies {
                ${testFrameworkDependencies}
            }

            test {
                ${configureTestFramework} {
                    includeEngines 'does-not-exist'
                }
                testLogging {
                    minGranularity = 1
                    exceptionFormat = "FULL"
                }
            }
        """

        when:
        executer.withStackTraceChecksDisabled()
        fails "test"

        then:
        output.contains("org.junit.platform.commons.JUnitException: No TestEngine ID matched the following include EngineFilters: [does-not-exist]")
    }
}
