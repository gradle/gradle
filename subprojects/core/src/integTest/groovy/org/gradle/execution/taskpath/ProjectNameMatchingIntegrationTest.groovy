/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.execution.taskpath

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ProjectNameMatchingIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """
            rootProject.name = "root"

            include("projectA")
            include("projectA:projectSubA")
            includeBuild("includedBuild")
        """.stripIndent()

        // Set-up subprojects
        file("projectA/projectSubA").mkdirs()

        // Set-up included project
        file("includedBuild/projectI/projectSubI").mkdirs()
        file("includedBuild/settings.gradle") << """
            include("projectI")
            include("projectI:projectSubI")
        """.stripIndent()
    }

    def "logs info message for exact project name match [#desc]"() {
        when:
        run("$projectPath:help", "--info")

        then:
        outputContains("Task path '$projectPath:help' matched project '$resolvedProject'")
        outputDoesNotContain("abbreviated")

        where:
        desc                                    | projectPath                           | resolvedProject
        "root (no colon)"                       | ""                                    | ":"                      // Global root
        "projectA"                              | ":projectA"                           | ":projectA"              // Subprojects
        "projectA/projectSubA"                  | ":projectA:projectSubA"               | ":projectA:projectSubA"
        "included build - included root"        | ":includedBuild"                      | ":includedBuild"         // Included build projects
        "included build - projectI"             | ":includedBuild:projectI"             | ":includedBuild:projectI"
        "included build - projectI/projectSubI" | ":includedBuild:projectI:projectSubI" | ":includedBuild:projectI:projectSubI"
    }

    def "logs info message for project name pattern match [#desc]"() {
        when:
        run("$projectPath:help", "--info")

        then:
        outputContains("Task path '$projectPath:help' matched project '$expectedProjectPath'")

        where:
        desc                                    | projectPath                   | expectedProjectPath
        "projectA"                              | ":pA"                         | ":projectA"               // Subprojects
        "projectA/projectSubA"                  | ":pA:projectSubA"             | ":projectA:projectSubA"
        "projectA/projectSubA"                  | ":projectA:pSA"               | ":projectA:projectSubA"
        "included build - projectI"             | ":includedBuild:pI"           | ":includedBuild:projectI" // Included build projects
        "included build - projectI/projectSubI" | ":includedBuild:projectI:pSI" | ":includedBuild:projectI:projectSubI"
    }

}
