/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.testing.internal.util.RetryUtil

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // Gradle API and TestKit JARs are not generated when running embedded
class GradleImplDepsPerformanceIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FLAKY)
    def "Gradle API JAR is generated in an acceptable time frame"() {
        buildFile << """
            configurations {
                deps
            }

            dependencies {
                deps gradleApi()
            }
        """
        buildFile << resolveDependencies(5000)

        expect:
        RetryUtil.retry {
            succeeds 'resolveDependencies'
            executer.gradleUserHomeDir.deleteDir()
        }
    }

    @ToBeFixedForConfigurationCache(skip = ToBeFixedForConfigurationCache.Skip.FLAKY)
    def "TestKit JAR is generated in an acceptable time frame"() {
        buildFile << """
            configurations {
                deps
            }

            dependencies {
                deps gradleTestKit()
            }
        """
        buildFile << resolveDependencies(2000)

        expect:
        RetryUtil.retry {
            succeeds 'resolveDependencies'
            executer.gradleUserHomeDir.deleteDir()
        }
    }

    static String resolveDependencies(long maxMillis) {
        """
            task resolveDependencies {
                doLast {
                    def timeStart = new Date()
                    configurations.deps.resolve()
                    def timeStop = new Date()
                    def duration = groovy.time.TimeCategory.minus(timeStop, timeStart)
                    assert duration.toMilliseconds() < $maxMillis
                }
            }
        """
    }
}
