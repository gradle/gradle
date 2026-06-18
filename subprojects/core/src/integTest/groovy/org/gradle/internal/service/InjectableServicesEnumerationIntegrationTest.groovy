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

package org.gradle.internal.service

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

/**
 * Demonstrates programmatically listing the services injectable from a project's service registry,
 * filtered to the public ones (no {@code internal} package segment). This exercises
 * {@link ServiceRegistryIntrospection#getAllServiceTypes()} against a real project, to produce an
 * up-to-date list to reconcile against the documented injectable services.
 */
@Issue("https://github.com/gradle/gradle/issues/38028")
class InjectableServicesEnumerationIntegrationTest extends AbstractIntegrationSpec {

    def "can list the public services injectable from a project's service registry"() {
        given:
        buildFile """
            // Enumerate at configuration time and capture only Strings, so the task stays
            // configuration-cache compatible (no live service/registry held by the task).
            def publicServiceTypes = (project.services as ${ServiceRegistryIntrospection.name})
                .allServiceTypes
                .collect { it.name }
                .findAll { it.startsWith("org.gradle.") && !it.contains(".internal.") }
                .unique()
                .sort()

            tasks.register("listInjectableServices") {
                doLast {
                    publicServiceTypes.each { println "INJECTABLE_SERVICE: \$it" }
                    println "INJECTABLE_SERVICE_COUNT: \${publicServiceTypes.size()}"
                }
            }
        """

        when:
        run "listInjectableServices"

        then:
        // documented project-injectable services that resolve from project scope or an ancestor
        outputContains("INJECTABLE_SERVICE: org.gradle.api.model.ObjectFactory")
        outputContains("INJECTABLE_SERVICE: org.gradle.api.provider.ProviderFactory")
        outputContains("INJECTABLE_SERVICE: org.gradle.api.file.FileSystemOperations")
        outputContains("INJECTABLE_SERVICE: org.gradle.api.file.ProjectLayout")

        and:
        def listed = output.readLines()
            .findAll { it.startsWith("INJECTABLE_SERVICE: ") }
            .collect { it.substring("INJECTABLE_SERVICE: ".length()) }
        // the filter actually filtered: every entry is a public org.gradle type
        listed.every { it.startsWith("org.gradle.") && !it.contains(".internal.") }
        // and there is a meaningful number of them
        listed.size() >= 5
    }
}
