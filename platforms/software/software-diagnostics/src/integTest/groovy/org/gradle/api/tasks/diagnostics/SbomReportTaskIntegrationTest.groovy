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
package org.gradle.api.tasks.diagnostics

import groovy.json.JsonSlurper
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SbomReportTaskIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        mavenRepo.module("org", "leaf1").publish()
        mavenRepo.module("org", "leaf2").dependsOnModules("leaf1").publish()
        buildFile << """
            apply plugin: 'java'
            repositories { maven { url = "${mavenRepo.uri}" } }
            dependencies { implementation 'org:leaf2:1.0' }
        """
    }

    def "writes a protobom json file for runtimeClasspath"() {
        when:
        run "sbom"

        then:
        def report = new JsonSlurper().parse(file("build/reports/sbom/sbom.json"))
        report.metadata.tools[0].name == "Gradle"
        report.nodeList.nodes.find { it.identifiers?.PURL == "pkg:maven/org/leaf2@1.0" } != null
        report.nodeList.edges.every { it.type == "dependsOn" }
        report.nodeList.rootElements.every { it.toString().startsWith("root project") }
    }

    def "output file and configurations can be configured"() {
        given:
        buildFile << """
            tasks.named("sbom") {
                output = layout.buildDirectory.file("reports/other.json")
                configurations = ["compileClasspath"]
            }
        """

        when:
        run "sbom"

        then:
        file("build/reports/other.json").text.contains("pkg:maven/org/leaf2@1.0")
        !file("build/reports/sbom/sbom.json").exists()
    }

    def "--configuration selects another configuration"() {
        when:
        run "sbom", "--configuration", "annotationProcessor"

        then:
        def report = new JsonSlurper().parse(file("build/reports/sbom/sbom.json"))
        report.nodeList.nodes.every { it.identifiers == null }
        !report.nodeList.nodes.any { it.identifiers?.PURL == "pkg:maven/org/leaf2@1.0" }
    }
}
