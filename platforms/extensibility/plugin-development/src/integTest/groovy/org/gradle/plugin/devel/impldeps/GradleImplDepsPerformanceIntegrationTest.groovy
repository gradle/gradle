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


import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // Gradle API and TestKit JARs are not generated when running embedded
class GradleImplDepsPerformanceIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "#dependency JAR is generated in an acceptable time frame"() {
        buildFile """
            configurations {
                deps
            }

            dependencies {
                deps ${declaration}
            }

            task resolveDependencies {
                def depsConf = configurations.named("deps")
                doLast {
                    println("Resolved dependencies: " + depsConf.get().size())
                }
            }
        """

        when:
        succeeds("resolveDependencies")

        then:
        def generation = operations.only("Generate $dependency jar")
        def durationMs = generation.endTime - generation.startTime
        durationMs < 8000

        where:
        dependency       | declaration
        "Gradle API"     | "gradleApi()"
        "Gradle TestKit" | "gradleTestKit()"
    }
}
