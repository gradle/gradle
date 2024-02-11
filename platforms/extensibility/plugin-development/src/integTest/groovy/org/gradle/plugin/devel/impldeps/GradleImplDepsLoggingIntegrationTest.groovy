/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions
import org.gradle.util.GradleVersion

@Requires(IntegTestPreconditions.NotEmbeddedExecutor) // Gradle API and TestKit JARs are not generated when running embedded
class GradleImplDepsLoggingIntegrationTest extends BaseGradleImplDepsIntegrationTest {

    def "Generating Gradle API jar is logged with rich console"() {
        given:
        executer.withTestConsoleAttached()
        executer.withConsole(ConsoleOutput.Rich)
        buildFile << """
            configurations {
                gradleImplDeps
            }

            dependencies {
                gradleImplDeps gradleApi()
            }

            task resolveDependencies {
                doLast {
                    configurations.gradleImplDeps.resolve()
                }
            }
        """

        when:
        succeeds('resolveDependencies')

        then:
        def gradleVersion = GradleVersion.current().version
        output.contains("Generating gradle-api-${gradleVersion}.jar")
    }
}
