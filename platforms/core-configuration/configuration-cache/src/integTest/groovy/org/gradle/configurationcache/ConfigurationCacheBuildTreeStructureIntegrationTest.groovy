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
                            def builds = []
                            registry.visitBuilds { b ->
                                builds.add(b.identityPath.path)
                                if (b.projectsLoaded) {
                                    projects.addAll(b.projects.allProjects.collect { p -> p.identityPath.path })
                                }
                            }
                            println "projects = " + projects
                            println "builds = " + builds
                        }
                    }
                }
            }
        """
    }

    def "restores only projects that have work scheduled"(List<String> tasks) {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)
        executer.beforeExecute {
            withArgument("-Dorg.gradle.configuration-cache.internal.load-after-store=true")
        }
        createDirs("a", "b", "c", "custom")
        settingsFile << """
            rootProject.name = 'thing'
            include 'a', 'b', 'c'
            include 'a:b'
            project(':a:b').projectDir = file('custom')
            project(':c').buildFileName = 'custom.gradle.kts'
        """
        defineTaskInSettings(settingsFile)

        when:
        configurationCacheRun(tasks as String[])

        then:
        with(fixture.only(LoadBuildBuildOperationType)) {
            it.details.buildPath == ":"
        }
        with(fixture.only(LoadProjectsBuildOperationType)) {
            details.buildPath == ":"
            result.buildPath == ":"
            checkProjects(result.rootProject)
        }
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 1
            first().details.buildPath == ":"
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 1
            with(first()) {
                details.buildPath == ":"
                checkProjects(details.rootProject)
            }
        }

        when:
        configurationCacheRun(tasks as String[])

        then:
        if (projects) {
            outputContains("builds = [:]")
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
                details.buildPath == ":"
                checkProjects(details.rootProject)
            }
        }

        where:
        tasks          | projects
        ["help"]       | null
        [":thing"]     | [':']
        [":a:thing"]   | [':', ':a']
        [":a:b:thing"] | [':', ':a', ':a:b']
    }

    void checkProjects(def rootProject) {
        assert rootProject.name == 'thing'
        assert rootProject.path == ':'
        assert rootProject.projectDir == testDirectory.absolutePath
        assert rootProject.buildFile == buildFile.absolutePath
        assert rootProject.children.size() == 3 // All projects are created when storing
        with(rootProject.children.first() as Map<String, Object>) {
            assert name == 'a'
            assert path == ':a'
            assert projectDir == file('a').absolutePath
            assert buildFile == file('a/build.gradle').absolutePath
            with(children.first()) {
                assert name == 'b'
                assert path == ':a:b'
                assert projectDir == file('custom').absolutePath
                assert buildFile == file('custom/build.gradle').absolutePath
                assert children.empty
            }
        }
        with(rootProject.children[2] as Map<String, Object>) {
            assert name == 'c'
            assert buildFile == file('c/custom.gradle.kts').absolutePath
        }
    }

    def "restores only projects that have work scheduled when buildSrc present"() {
        def fixture = new BuildOperationsFixture(executer, temporaryFolder)
        createDirs("a", "b", "c")
        settingsFile << """
            rootProject.name = 'thing'
            include 'a', 'b', 'c'
        """
        defineTaskInSettings(settingsFile)
        createDirs("buildSrc/a", "buildSrc/b")
        file("buildSrc/settings.gradle") << """
            include 'a', 'b'
        """
        file("buildSrc/src/main/java/Lib.java") << """
            class Lib { }
        """

        when:
        configurationCacheRun(":a:thing")

        then:
        with(fixture.all(LoadBuildBuildOperationType)) {
            size() == 2
            with(get(0)) {
                details.buildPath == ':'
            }
            with(get(1)) {
                details.buildPath == ':buildSrc'
            }
        }
        with(fixture.all(LoadProjectsBuildOperationType)) {
            size() == 2
            with(get(0)) {
                result.rootProject.name == 'thing'
                result.rootProject.path == ':'
                result.rootProject.children.size() == 3
                with(result.rootProject.children.first() as Map<String, Object>) {
                    name == 'a'
                    path == ':a'
                    projectDir == file('a').absolutePath
                    children.empty
                }
            }
            with(get(1)) {
                result.rootProject.name == 'buildSrc'
                result.rootProject.path == ':'
                result.rootProject.children.size() == 2
            }
        }
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 2
            with(get(0)) {
                details.buildPath == ':'
            }
            with(get(1)) {
                details.buildPath == ':buildSrc'
            }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 2
            with(get(0)) {
                details.rootProject.name == 'thing'
                details.rootProject.path == ':'
                details.rootProject.projectDir == testDirectory.absolutePath
                details.rootProject.children.size() == 3
                with(details.rootProject.children.first() as Map<String, Object>) {
                    name == 'a'
                    path == ':a'
                    projectDir == file('a').absolutePath
                    children.empty
                }
            }
        }

        when:
        configurationCacheRun(":a:thing")

        then:
        outputContains("builds = [:, :buildSrc]")
        outputContains("projects = [:, :a]")
        fixture.none(LoadBuildBuildOperationType)
        fixture.none(LoadProjectsBuildOperationType)
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            with(fixture.progress(BuildIdentifiedProgressDetails)) {
                size() == 2
                with(get(0)) {
                    details.buildPath == ':'
                }
                with(get(1)) {
                    details.buildPath == ':buildSrc'
                }
            }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 2
            with(first()) {
                details.rootProject.name == 'thing'
                details.rootProject.path == ':'
                details.rootProject.projectDir == testDirectory.absolutePath
                details.rootProject.children.size() == 3
                with(details.rootProject.children.first() as Map<String, Object>) {
                    name == 'a'
                    path == ':a'
                    projectDir == file('a').absolutePath
                    children.empty
                }
            }
        }
    }

    def "restores only builds and projects of included build that have work scheduled"(String task) {
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
        createDirs("include/inner-include/child", "include/child", "child")
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
            it.find { it.details.buildPath == ":include:inner-include" }
        }
        with(fixture.all(LoadProjectsBuildOperationType)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":include:inner-include" }
        }
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":include:inner-include" }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 3
            with(it.find { it.details.buildPath == ":" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include:inner-include" }) {
                details.rootProject.children.size() == 1
            }
        }

        when:
        configurationCacheRun(task)

        then:
        outputContains("builds = $builds")
        outputContains("projects = $projects")
        fixture.none(LoadBuildBuildOperationType)
        fixture.none(LoadProjectsBuildOperationType)
        with(fixture.progress(BuildIdentifiedProgressDetails)) {
            size() == 3
            it.find { it.details.buildPath == ":" }
            it.find { it.details.buildPath == ":include" }
            it.find { it.details.buildPath == ":include:inner-include" }
        }
        with(fixture.progress(ProjectsIdentifiedProgressDetails)) {
            size() == 3
            with(it.find { it.details.buildPath == ":" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include" }) {
                details.rootProject.children.size() == 1
            }
            with(it.find { it.details.buildPath == ":include:inner-include" }) {
                details.rootProject.children.size() == 1
            }
        }

        where:
        task                                 | projects                                                        | builds
        ":thing"                             | [':']                                                           | [':']
        ":child:thing"                       | [':', ':child']                                                 | [':']
        ":include:thing"                     | [':', ':include']                                               | [':', ':include']
        ":include:child:thing"               | [':', ':include', ':include:child']                             | [':', ':include']
        ":include:inner-include:thing"       | [':', ':include:inner-include']                                 | [':', ':include:inner-include']
        ":include:inner-include:child:thing" | [':', ':include:inner-include', ':include:inner-include:child'] | [':', ':include:inner-include']
    }
}
