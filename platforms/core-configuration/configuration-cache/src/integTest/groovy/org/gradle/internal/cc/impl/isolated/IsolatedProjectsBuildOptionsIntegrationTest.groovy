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

package org.gradle.internal.cc.impl.isolated

class IsolatedProjectsBuildOptionsIntegrationTest extends AbstractIsolatedProjectsIntegrationTest {

    private static final String LEGACY_IP = "org.gradle.unsafe.isolated-projects"
    private static final String LEGACY_DIAGNOSTICS = "org.gradle.unsafe.isolated-projects.diagnostics"
    private static final String LEGACY_DANGEROUSLY_IGNORE_PROBLEMS = "org.gradle.unsafe.isolated-projects.dangerously-ignore-problems"

    def setup() {
        buildFile """
            import org.gradle.api.configuration.BuildFeatures

            def buildFeatures = gradle.services.get(BuildFeatures)
            tasks.register("something") {
                doLast {
                    println "isolatedProjects.requested=" + buildFeatures.isolatedProjects.requested.getOrNull()
                    println "isolatedProjects.active=" + buildFeatures.isolatedProjects.active.get()
                }
            }
        """
    }

    def "Isolated Projects is disabled by default"() {
        when:
        run "something"

        then:
        outputContains("isolatedProjects.requested=null")
        outputContains("isolatedProjects.active=false")
    }

    def "property enables Isolated Projects"() {
        when:
        run "something", "-Dorg.gradle.isolated-projects=true"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "deprecated property still enables Isolated Projects"() {
        when:
        run "something", "-D${LEGACY_IP}=true"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "gradle.properties enables Isolated Projects"() {
        given:
        file("gradle.properties") << "org.gradle.isolated-projects=true"

        when:
        run "something"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "--isolated-projects enables Isolated Projects"() {
        when:
        run "something", "--isolated-projects"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "--no-isolated-projects disables Isolated Projects"() {
        when:
        run "something", "--no-isolated-projects"

        then:
        outputContains("isolatedProjects.requested=false")
        outputContains("isolatedProjects.active=false")
    }

    def "build option takes precedence over property when disabling"() {
        when:
        run "something", "--no-isolated-projects", "-Dorg.gradle.isolated-projects=true"

        then:
        outputContains("isolatedProjects.requested=false")
        outputContains("isolatedProjects.active=false")
    }

    def "build option takes precedence over property when enabling"() {
        when:
        run "something", "--isolated-projects", "-Dorg.gradle.isolated-projects=false"

        then:
        outputContains("isolatedProjects.requested=true")
        outputContains("isolatedProjects.active=true")
    }

    def "diagnostics option is accepted under both property names"() {
        when:
        run "something", "-q",
            "-Dorg.gradle.internal.operations.verbose.parameters=true",
            "-Dorg.gradle.isolated-projects=true",
            "-D${propertyName}=true"

        then:
        result.getOutputLineThatContains("Operational build model parameters:").contains("isolatedProjectsDiagnostics=true")

        where:
        propertyName << ["org.gradle.isolated-projects.diagnostics", LEGACY_DIAGNOSTICS]
    }

    def "dangerously-ignore-problems option is accepted under both property names"() {
        when:
        run "something", "-q",
            "-Dorg.gradle.internal.operations.verbose.parameters=true",
            "-Dorg.gradle.isolated-projects=true",
            "-D${propertyName}=true"

        then:
        result.getOutputLineThatContains("Operational build model parameters:").contains("isolatedProjectsDangerouslyIgnoreProblems=true")

        where:
        propertyName << ["org.gradle.isolated-projects.dangerously-ignore-problems", LEGACY_DANGEROUSLY_IGNORE_PROBLEMS]
    }
}
