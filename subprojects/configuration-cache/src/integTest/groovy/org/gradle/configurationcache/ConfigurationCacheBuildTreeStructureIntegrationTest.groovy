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

package org.gradle.configurationcache

import org.gradle.initialization.BuildIdentifiedProgressDetails
import org.gradle.initialization.LoadBuildBuildOperationType
import org.gradle.initialization.LoadProjectsBuildOperationType
import org.gradle.initialization.ProjectsIdentifiedProgressDetails
import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.test.fixtures.file.TestFile

class ConfigurationCacheBuildTreeStructureIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    private void defineTaskInSettings(TestFile settingsFile) {
        settingsFile << """
            gradle.rootProject {
                allprojects {
                    task thing {
                        def registry = project.services.get(${BuildStateRegistry.name})
                        doLast {
                            def projects = []
                            registry.visitBuilds { b ->
                                projects.addAll(b.projects.allProjects.collect { p -> p.identityPath.path })
                            }
                            println "projects = " + projects
                        }
                    }
                }
            }
        """
    }

    def "restores some details of the project structure"(List<String> tasks) {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.configuration-cache.internal.load-after-store=true")
        }
        settingsFile << """
            rootProject.name = 'thing'
            include 'a', 'b', 'c'
            include 'a:b'
            project(':a:b').projectDir = file('custom')
        """
        defineTaskInSettings(settingsFile)

        when:
        configurationCacheRun(tasks as String[])

        then:
        with(fixture.only(LoadBuildBuildOperationType)) {
            it.details.buildPath == ":"
        }
        with(fixture.only(LoadProjectsBuildOperationType)) {
            result.rootProject.name == 'thing'
            result.rootProject.path == ':'
            result.rootProject.children.size() == 3 // All projects are created when storing
            with(result.rootProject.children.first() as Map<String, Object>) {
                name == 'a'
                path == ':a'
                projectDir == file('a').absolutePath
                with(children.first()) {
                    name == 'b'
                    path == ':a:b'
                    projectDir == file('custom').absolutePath
                    children.empty
                }
            }
        }
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 1
            first().details.buildPath == ":"
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 1
            with(first()) {
                details.rootProject.name == 'thing'
                details.rootProject.path == ':'
                details.rootProject.projectDir == testDirectory.absolutePath
                details.rootProject.children.size() == 3
                with(details.rootProject.children.first() as Map<String, Object>) {
                    name == 'a'
                    path == ':a'
                    projectDir == file('a').absolutePath
                    with(children.first()) {
                        name == 'b'
                        path == ':a:b'
                        projectDir == file('custom').absolutePath
                        children.empty
                    }
                }
            }
        }

        when:
        configurationCacheRun(tasks as String[])

        then:
        if (projects) {
            outputContains("projects = $projects")
        }
        fixture.none(LoadBuildBuildOperationType)
        fixture.none(LoadProjectsBuildOperationType)
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 1
            first().details.buildPath == ":"
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 1
            with(first()) {
                details.rootProject.name == 'thing'
                details.rootProject.path == ':'
                details.rootProject.projectDir == testDirectory.absolutePath
                details.rootProject.children.size() == 3
                with(details.rootProject.children.first() as Map<String, Object>) {
                    name == 'a'
                    path == ':a'
                    projectDir == file('a').absolutePath
                    children.size() == 1
                    with(children.first() as Map<String, Object>) {
                        name == 'b'
                        path == ':a:b'
                        projectDir == file('custom').absolutePath
                    }
                }
            }
        }

        where:
        tasks          | projects
        ["help"]       | null
        [":thing"]     | [':']
        [":a:thing"]   | [':', ':a']
        [":a:b:thing"] | [':', ':a', ':a:b']
    }

    def "restores some details of the project structure of included build"(String task) {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.configuration-cache.internal.load-after-store=true")
        }

        createDir("include") {
            dir("inner-include") {
                file("settings.gradle") << """
                    rootProject.name = "inner-include"
                    include "child"
                """
                defineTaskInSettings(file("settings.gradle"))
            }
            file("settings.gradle") << """
                rootProject.name = "include"
                includeBuild "inner-include"
                include "child"
            """
            defineTaskInSettings(file("settings.gradle"))
        }
        settingsFile << """
            rootProject.name = 'thing'
            includeBuild "include"
            include "child"
        """
        defineTaskInSettings(settingsFile)

        when:
        configurationCacheRun(task)

        then:
        with(fixture.all(LoadBuildBuildOperationType)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":inner-include" }
        }
        with(fixture.all(LoadProjectsBuildOperationType)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":inner-include" }
        }
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":inner-include" }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 3
            with(it.find { it.details.buildPath == ":" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":inner-include" }) {
                details.rootProject.children.size() == 1
            }
        }

        when:
        configurationCacheRun(task)

        then:
        outputContains("projects = $projects")
        fixture.none(LoadBuildBuildOperationType)
        fixture.none(LoadProjectsBuildOperationType)
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":inner-include" }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 3
            with(it.find { it.details.buildPath == ":" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":inner-include" }) {
                details.rootProject.children.size() == 1
            }
        }

        where:
        task                         | projects
        ":thing"                     | [':', ':include', ':inner-include']
        ":child:thing"               | [':', ':child', ':include', ':inner-include']
        ":include:thing"             | [':', ':include', ':inner-include']
        ":include:child:thing"       | [':', ':include', ':include:child', ':inner-include']
        ":inner-include:thing"       | [':', ':include', ':inner-include']
        ":inner-include:child:thing" | [':', ':include', ':inner-include', ':inner-include:child']
    }
}
