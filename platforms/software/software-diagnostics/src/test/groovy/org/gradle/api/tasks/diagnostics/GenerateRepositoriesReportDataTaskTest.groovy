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

import org.gradle.api.tasks.diagnostics.internal.repositories.json.RepositoryReportDataJsonReader
import org.gradle.api.tasks.diagnostics.internal.repositories.model.RepositoryRole
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.Path

class GenerateRepositoriesReportDataTaskTest extends AbstractProjectBuilderSpec {

    def "task captures own project's repositories and writes JSON"() {
        given:
        project.with {
            repositories {
                mavenCentral()
                google()
            }
            buildscript {
                repositories {
                    mavenCentral()
                }
            }
        }

        def task = project.tasks.register("generateRepoData", GenerateRepositoriesReportDataTask) {
            outputFile = project.layout.buildDirectory.file("test-out.json")
        }.get()

        when:
        task.generate()

        then:
        def model = RepositoryReportDataJsonReader.read(task.outputFile.get().asFile)
        model.projectPath == Path.path(":")
        model.projectName == project.name
        model.projectRepositories.size() == 2
        model.projectRepositories*.name == ["MavenRepo", "Google"]
        model.projectRepositories*.roles.every { it == [RepositoryRole.PROJECT_DEPENDENCIES] as Set }
        model.buildscriptRepositories.size() == 1
        model.buildscriptRepositories[0].roles == [RepositoryRole.PROJECT_BUILDSCRIPT_DEPENDENCIES] as Set
    }

    def "task captures empty repositories when none declared"() {
        given:
        def task = project.tasks.register("generateRepoData", GenerateRepositoriesReportDataTask) {
            outputFile = project.layout.buildDirectory.file("empty.json")
        }.get()

        when:
        task.generate()

        then:
        def model = RepositoryReportDataJsonReader.read(task.outputFile.get().asFile)
        model.projectRepositories.isEmpty()
        model.buildscriptRepositories.isEmpty()
    }
}
