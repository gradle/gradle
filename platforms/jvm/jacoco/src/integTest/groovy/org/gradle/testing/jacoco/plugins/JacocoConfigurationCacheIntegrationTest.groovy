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

package org.gradle.testing.jacoco.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import spock.lang.Issue

@Requires(value = IntegTestPreconditions.NotConfigCached, reason = 'Test explicitly enables the configuration cache')
class JacocoConfigurationCacheIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        // run all tests with configuration caching
        file('gradle.properties') << 'org.gradle.configuration-cache=true'
    }

    @Issue('https://github.com/gradle/gradle/issues/26922')
    def 'can aggregate with `java-gradle-plugin` subproject'() {
        given:
        file('settings.gradle.kts') << """
            include(":plugin")
            dependencyResolutionManagement {
                ${mavenCentralRepository(GradleDsl.KOTLIN)}
            }
        """
        file('build.gradle.kts') << '''
            plugins { id("jacoco-report-aggregation") }

            reporting {
                reports {
                    val testCodeCoverageReport by creating(JacocoCoverageReport::class) {
                        testType = TestSuiteType.UNIT_TEST
                    }
                }
            }

            dependencies {
                jacocoAggregation(project(":plugin"))
            }
        '''
        createDir('plugin') {
            file('build.gradle.kts') << '''
                plugins { id("java-gradle-plugin") }
            '''
        }

        expect:
        succeeds ':testCodeCoverageReport'
    }
}
