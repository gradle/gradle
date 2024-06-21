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
import org.gradle.internal.configurationcache.ConfigurationCacheCheckFingerprintBuildOperationType

class IsolatedProjectsBuildOperationsIntegrationTest extends AbstractIsolatedProjectsToolingApiIntegrationTest {
    def operations = new BuildOperationsFixture(executer, temporaryFolder)

    def setup() {
        settingsFile """
            rootProject.name = 'root'
        """
    }

    def "emits no fingerprint operation when running without cache entry"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        when:
        fetchAllModels()

        then:
        operations.none(ConfigurationCacheCheckFingerprintBuildOperationType)
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
            buildInvalidationReasons == [
                [message: "file 'settings.gradle' has changed"]
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
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":",
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

        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        file("b/build.gradle") << """
            // Not applying the plugin intentionally
        """

        initializeCache()

        when: "subproject script is invalidated"
        file("a/build.gradle") << """
            println("project a updated")
        """
        fetchAllModels()

        then: "emits sub project invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":a",
                    invalidationReasons: [
                        [message: "file 'a/build.gradle' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file 'a/build.gradle' has changed")
    }

    def "emits fingerprint check operation when invalidating multiple subprojects"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        file("b/build.gradle") << """
            // Not applying the plugin intentionally
        """

        initializeCache()

        when: "subproject scripts are invalidated"
        file("a/build.gradle") << """
            println("project a updated")
        """
        file("b/build.gradle") << """
            println("project b updated")
        """
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":a",
                    invalidationReasons: [
                        [message: "file 'a/build.gradle' has changed"]
                    ]
                ],
                [
                    identityPath: ":b",
                    invalidationReasons: [
                        [message: "file 'b/build.gradle' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file 'a/build.gradle' has changed")
        outputDoesNotContain("file 'b/build.gradle' has changed")
    }

    def "emits fingerprint check operation when invalidating dependency"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":a"))
            }
        """

        initializeCache()

        when: "dependent script is invalidated"
        file("a/build.gradle") << """
            println("project a updated")
        """
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":a",
                    invalidationReasons: [
                        [message: "file 'a/build.gradle' has changed"]
                    ]
                ],
                [
                    identityPath: ":b",
                    invalidationReasons: [
                        [message: "project dependency has changed"]
                    ]
                ]
            ]
        }
        outputContains("file 'a/build.gradle' has changed")
        outputDoesNotContain("project dependency has changed")
    }

    def "emits fingerprint check operation with invalidated project being first"() {
        given:
        withSomeToolingModelBuilderPluginThatPerformsDependencyResolutionInBuildSrc()

        settingsFile """
            include("a")
            include("b")
        """

        file("a/build.gradle") << """
            plugins.apply(my.MyPlugin)
            dependencies {
                implementation(project(":b"))
            }
        """

        file("b/build.gradle") << """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when: "dependent script is invalidated"
        file("b/build.gradle") << """
            println("project b updated")
        """
        fetchAllModels()

        then: "emits subproject invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":b",
                    invalidationReasons: [
                        [message: "file 'b/build.gradle' has changed"]
                    ]
                ],
                [
                    identityPath: ":a",
                    invalidationReasons: [
                        [message: "project dependency has changed"]
                    ]
                ],
            ]
        }
        outputContains("file 'b/build.gradle' has changed")
        outputDoesNotContain("project dependency has changed")
    }

    def "emits fingerprint check operation when invalidating included build"() {
        given:
        withSomeToolingModelBuilderPluginInBuildSrc()

        settingsFile """
            includeBuild("a")
        """

        file("a/settings.gradle") << """
            rootProject.name = 'a'
        """

        buildFile """
            plugins.apply(my.MyPlugin)
        """

        initializeCache()

        when: "included project script is invalidated"
        file("a/build.gradle") << """
            println("included project updated")
        """
        fetchAllModels()

        then: "emits sub project invalidation reason"
        with(operations.only(ConfigurationCacheCheckFingerprintBuildOperationType).result) {
            buildInvalidationReasons == []
            projectInvalidationReasons == [
                [
                    identityPath: ":a",
                    invalidationReasons: [
                        [message: "file 'a/build.gradle' has changed"]
                    ]
                ]
            ]
        }
        outputContains("file 'a/build.gradle' has changed")
    }

    private def fetchAllModels() {
        executer.withArguments(ENABLE_CLI)
        return runBuildAction(new FetchCustomModelForEachProject())
    }

    private def initializeCache() {
        fetchAllModels()
    }
}
