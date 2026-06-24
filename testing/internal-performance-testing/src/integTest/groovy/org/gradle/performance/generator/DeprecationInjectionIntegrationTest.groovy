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

package org.gradle.performance.generator

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DeprecationInjectionIntegrationTest extends AbstractIntegrationSpec {

    def "generated project fires deprecationsPerProject x subProjects deprecations"() {
        given:
        def config = new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProjectDeprecations", "largeJavaMultiProject")
            .withSubProjects(2)
            .withSourceFiles(1)
            .withDaemonMemory("512m")
            .withCompilerMemory("256m")
            .withDeprecationsPerProject(2)
            .create()
        new TestProjectGenerator(config).generate(testDirectory)
        def projectDir = file("largeJavaMultiProjectDeprecations")

        expect: "the plugin is generated and applied to each subproject via the plugins block"
        projectDir.file("buildSrc/src/main/java/perf/PerfDeprecationsPlugin.java").assertExists()
        projectDir.file("project0/build.gradle").text.contains("id 'perf-deprecations'")

        when: "configuration runs (the integ executer already shows all warnings)"
        executer.inDirectory(projectDir).noDeprecationChecks()
        def result = executer.withTasks("help").run()

        then: "exactly deprecationsPerProject x subProjects deprecations fire"
        result.output.count("Perf deprecation from ") == 2 * 2
    }

    def "no deprecations plugin is generated when the flag is zero"() {
        given:
        def config = new TestProjectGeneratorConfigurationBuilder("largeJavaMultiProject")
            .withSubProjects(2)
            .withSourceFiles(1)
            .withDaemonMemory("512m")
            .withCompilerMemory("256m")
            .create()
        new TestProjectGenerator(config).generate(testDirectory)
        def projectDir = file("largeJavaMultiProject")

        expect:
        projectDir.file("buildSrc/src/main/java/perf/PerfDeprecationsPlugin.java").assertDoesNotExist()
        !projectDir.file("project0/build.gradle").text.contains("perf-deprecations")
    }
}
