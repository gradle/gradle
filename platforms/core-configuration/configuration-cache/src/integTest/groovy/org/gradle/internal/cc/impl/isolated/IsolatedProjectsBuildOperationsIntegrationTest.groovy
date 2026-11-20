/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.integtests.fixtures.BuildOperationsFixture
import org.gradle.operations.configuration.ConfigurationCacheCheckFingerprintBuildOperationType
import org.gradle.tooling.model.GradleProject

class IsolatedProjectsBuildOperationsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile """
            rootProject.name = 'root'
        """
    }

    def "emits not found fingerprint operation when running without cache entry"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        when:
        fetchAllModels()

        then:
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "NOT_FOUND"
            buildInvalidationReasons == []
            projectInvalidationReasons == []
            originBuildInvocationId == null
        }
    }

    def "emits fingerprint operation when cache hits"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when:
        fetchAllModels()

        then:
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "VALID"
            buildInvalidationReasons == []
            projectInvalidationReasons == []
            originBuildInvocationId != null
        }
    }

    def "emits fingerprint check operation when invalidating build state"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when: "settings script is invalidated"
        settingsFile """
            println("settings updated")
        """
        fetchAllModels()

        then: "emits build invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "INVALID"
            buildInvalidationReasons == [
                [
                    buildPath: ":",
                    invalidationReasons: [
                        [message: "file 'settings.gradle' has changed"]
                    ]
                ]
            ]
            projectInvalidationReasons == []
            originBuildInvocationId != null
        }
        outputContains("file 'settings.gradle' has changed")
    }

    def "emits fingerprint check operation when invalidating single project"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when: "single project script is invalidated"
        buildFile """
            println("single project updated")
        """
        fetchAllModels()

        then: "emits project invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":",
                    projectPath: ":",
                    invalidationReasons: [
                        [message: "file 'build.gradle' has changed"]
                    ]
                ]
            ]
            originBuildInvocationId != null
        }
        outputContains("file 'build.gradle' has changed")
    }

    def "emits fingerprint check operation when invalidating subproject"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        def buildFileA = buildFile("a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """)

        buildFile("b/build.gradle", """
            // Not applying the plugin intentionally
        """)

        initializeCache()

        when: "subproject script is invalidated"
        buildFile(buildFileA, """
            println("project a updated")
        """)
        fetchAllModels()

        then: "emits sub project invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":",
                    projectPath: ":a",
                    invalidationReasons: [
                        [message: "file '${buildFileA.relativePathFromBase}' has changed"]
                    ]
                ]
            ]
            originBuildInvocationId != null
        }
        outputContains("file '${buildFileA.relativePathFromBase}' has changed")
    }

    def "emits fingerprint check operation when creating preferred subproject build file"() {
        given:
        settingsFile """
            include("a")
            include("b")
        """

        def buildFileA = file("a/build.gradle")
        buildFile("a/build.gradle.kts", """
            // using Kotlin DSL
        """)
        buildFile("b/build.gradle", """
            // project b
        """)

        withIsolatedProjects()
        fetchModel(GradleProject)

        when: "a newly preferred build file is created"
        buildFile(buildFileA, """
            // now using Groovy DSL
        """)
        withIsolatedProjects()
        fetchModel(GradleProject)

        then: "the build scope is not invalidated and project :a is invalidated"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons.any {
                it.projectPath == ":a" &&
                    it.invalidationReasons == [[message: "the file system entry '${buildFileA.relativePathFromBase}' has been created"]]
            }
            originBuildInvocationId != null
        }
        outputContains("the file system entry '${buildFileA.relativePathFromBase}' has been created")
    }

    def "emits fingerprint check operation when invalidating applied subproject script"() {
        given:
        settingsFile """
            include("a")
            include("b")
        """

        def appliedScriptA = file("a/plugin.gradle")
        buildFile("a/build.gradle", """
            apply from: 'plugin.gradle'
        """)
        buildFile(appliedScriptA, """
            println("project a plugin")
        """)

        buildFile("b/build.gradle", """
            // project b
        """)

        withIsolatedProjects()
        fetchModel(GradleProject)

        when: "an applied script plugin is invalidated"
        buildFile(appliedScriptA, """
            println("project a plugin updated")
        """)
        withIsolatedProjects()
        fetchModel(GradleProject)

        then: "the build scope is not invalidated and project :a is invalidated"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons.any {
                it.projectPath == ":a" &&
                    it.invalidationReasons == [[message: "file '${appliedScriptA.relativePathFromBase}' has changed"]]
            }
            originBuildInvocationId != null
        }
        outputContains("file '${appliedScriptA.relativePathFromBase}' has changed")
    }

    def "emits fingerprint check operation when invalidating multiple subprojects"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        def buildFileA = buildFile("a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """)

        def buildFileB = buildFile("b/build.gradle", """
            // Not applying the plugin intentionally
        """)

        initializeCache()

        when: "subproject scripts are invalidated"
        buildFile(buildFileA, """
            println("project a updated")
        """)
        buildFile(buildFileB, """
            println("project b updated")
        """)
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":",
                    projectPath: ":a",
                    invalidationReasons: [
                        [message: "file '${buildFileA.relativePathFromBase}' has changed"]
                    ]
                ],
                [
                    buildPath: ":",
                    projectPath: ":b",
                    invalidationReasons: [
                        [message: "file '${buildFileB.relativePathFromBase}' has changed"]
                    ]
                ]
            ]
            originBuildInvocationId != null
        }
        outputContains("file '${buildFileA.relativePathFromBase}' has changed")
        outputDoesNotContain("file '${buildFileB.relativePathFromBase}' has changed")
    }

    def "emits fingerprint check operation when invalidating dependency"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        def buildFileA = buildFile("a/build.gradle", """
            plugins.apply(my.MyPlugin)
        """)

        buildFile("b/build.gradle", """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":a"))
            }
        """)

        initializeCache()

        when: "dependent script is invalidated"
        buildFile(buildFileA, """
            println("project a updated")
        """)
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":",
                    projectPath: ":a",
                    invalidationReasons: [
                        [message: "file '${buildFileA.relativePathFromBase}' has changed"]
                    ]
                ],
                [
                    buildPath: ":",
                    projectPath: ":b",
                    invalidationReasons: [
                        [message: "project dependency ':a' has changed"]
                    ]
                ]
            ]
            originBuildInvocationId != null
        }
        outputContains("file '${buildFileA.relativePathFromBase}' has changed")
        outputDoesNotContain("project dependency ':a' has changed")
    }

    def "emits fingerprint check operation with invalidated project being first"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        buildFile("a/build.gradle", """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """)

        def buildFileB = buildFile("b/build.gradle", """
            plugins.apply(my.MyPlugin)
        """)

        initializeCache()

        when: "dependent script is invalidated"
        buildFile(buildFileB, """
            println("project b updated")
        """)
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":",
                    projectPath: ":b",
                    invalidationReasons: [
                        [message: "file '${buildFileB.relativePathFromBase}' has changed"]
                    ]
                ],
                [
                    buildPath: ":",
                    projectPath: ":a",
                    invalidationReasons: [
                        [message: "project dependency ':b' has changed"]
                    ]
                ],
            ]
            originBuildInvocationId != null
        }
        outputContains("file '${buildFileB.relativePathFromBase}' has changed")
        outputDoesNotContain("project dependency ':b' has changed")
    }

    def "single subproject build script edit only invalidates that project under parallel IP"() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include("a")
        """

        def buildFileA = buildFile "a/build.gradle", """
            // project a
        """

        executer.beforeExecute {
            it.withArgument("-Dorg.gradle.internal.isolated-projects.parallel=true")
            it.withArgument("-Dorg.gradle.workers.max=8")
            it.withArgument(ENABLE_CLI)
        }

        and: "first model fetch populates the configuration cache"
        fetchModel(GradleProject)

        when: "the subproject's build script is edited"
        buildFileA.text = """
            // project a updated
        """
        fetchModel(GradleProject)

        then: "only the edited project is invalidated, not the build scope"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons.size() == 1
            projectInvalidationReasons[0].buildPath == ":"
            projectInvalidationReasons[0].projectPath == ":a"
            projectInvalidationReasons[0].invalidationReasons == [[message: "file '${buildFileA.relativePathFromBase}' has changed".toString()]]
            originBuildInvocationId != null
        }
        outputContains("file '${buildFileA.relativePathFromBase}' has changed")
    }

    def "emits fingerprint check operation when invalidating included build"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        settingsFile """
            includeBuild("a")
        """

        settingsFile("a/settings.gradle", """
            rootProject.name = 'a'
        """)

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when: "included project script is invalidated"
        def buildFileA = buildFile("a/build.gradle", """
            println("included project updated")
        """)
        fetchAllModels()

        then: "emits sub project invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            status == "PARTIAL"
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    buildPath: ":a",
                    projectPath: ":",
                    invalidationReasons: [
                        [message: "the file system entry '${buildFileA.relativePathFromBase}' has been created"]
                    ]
                ]
            ]
            originBuildInvocationId != null
        }
        outputContains("the file system entry '${buildFileA.relativePathFromBase}' has been created")
    }

    private def fetchAllModels() {
        withIsolatedProjects()
        return runBuildAction(new FetchCustomModelForEachProject())
    }

    private def initializeCache() {
        fetchAllModels()
    }

}
