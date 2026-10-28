/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.services

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildOperationsFixture
import spock.lang.Issue

// On a configuration cache miss, the services that are not kept alive can also be closed when the models are reset.
// Thus, only the presence of the operations is checked, not their number.
@Issue("https://github.com/gradle/gradle/issues/39528")
class BuildServiceBuildOperationsIntegrationTest extends AbstractIntegrationSpec {

    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def "closes a build service in a build operation"() {
        given:
        buildFile << service("CloseableService", "implements AutoCloseable", "void close() { println 'Service closed' }")
        buildFile << usingTask()

        when:
        succeeds 'use'

        then:
        outputContains 'Service closed'
        def closing = operations.matchingRegex("Close build service 'service' \\(CloseableService\\)")
        !closing.empty
        closing.every { operations.withId(it.id).failure == null }
    }

    def "does not close a build service without an instance in a build operation"() {
        given:
        buildFile << service("CloseableService", "implements AutoCloseable", "void close() {}")
        buildFile << "tasks.register('use')"

        when:
        succeeds 'use'

        then:
        operations.none(~/Close build service .*/)
    }

    def "does not close a build service that is not closeable in a build operation"() {
        given:
        buildFile << service("PlainService", "", "")
        buildFile << usingTask()

        when:
        succeeds 'use'

        then:
        operations.none(~/Close build service .*/)
    }

    def "fails the operation of a failing close"() {
        given:
        buildFile << service("CloseableService", "implements AutoCloseable", "void close() { throw new RuntimeException('broken') }")
        buildFile << usingTask()

        when:
        fails 'use'

        then:
        failure.assertHasFailure("Failed to stop service 'service'.") {
            it.assertHasCause("broken")
        }
        operations.matchingRegex("Close build service 'service' \\(CloseableService\\)").any { operations.withId(it.id).failure?.contains("Failed to stop service 'service'.") }
    }

    private static String service(String type, String interfaces, String members) {
        """
            import org.gradle.api.services.BuildService
            import org.gradle.api.services.BuildServiceParameters

            abstract class ${type} implements BuildService<BuildServiceParameters.None> ${interfaces ? ", " + interfaces - "implements " : ""} {
                ${members}
            }
            def service = gradle.sharedServices.registerIfAbsent("service", ${type}) {}
        """
    }

    private static String usingTask() {
        """
            tasks.register('use') {
                usesService(service)
                def provider = service
                doLast {
                    provider.get()
                }
            }
        """
    }
}
