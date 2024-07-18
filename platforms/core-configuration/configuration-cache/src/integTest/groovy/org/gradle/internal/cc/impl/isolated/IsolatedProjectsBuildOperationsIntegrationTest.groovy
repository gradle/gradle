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
import org.gradle.test.fixtures.file.TestFile

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
                        [message: "file '${relpath(buildFileA)}' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file '${relpath(buildFileA)}' has changed")
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
                        [message: "file '${relpath(buildFileA)}' has changed"]
                    ]
                ],
                [
                    buildPath: ":",
                    projectPath: ":b",
                    invalidationReasons: [
                        [message: "file '${relpath(buildFileB)}' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file '${relpath(buildFileA)}' has changed")
        outputDoesNotContain("file '${relpath(buildFileB)}' has changed")
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
                        [message: "file '${relpath(buildFileA)}' has changed"]
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
        }
        outputContains("file '${relpath(buildFileA)}' has changed")
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
                        [message: "file '${relpath(buildFileB)}' has changed"]
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
        }
        outputContains("file '${relpath(buildFileB)}' has changed")
        outputDoesNotContain("project dependency ':b' has changed")
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
                        [message: "file '${relpath(buildFileA)}' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file '${relpath(buildFileA)}' has changed")
    }

    private def fetchAllModels() {
        executer.withArguments(ENABLE_CLI)
        return runBuildAction(new FetchCustomModelForEachProject())
    }

    private def initializeCache() {
        fetchAllModels()
    }

    private String relpath(TestFile file) {
        return file.relativePathFromBase
    }
}
