/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.plugin.PluginBuilder

class CompositeBuildSettingsPluginIntegrationTest extends AbstractIntegrationSpec {

    @ToBeFixedForConfigurationCache(because="composite build")
    def "included build can apply settings plugins contributed by other included builds"() {
        file("build-src/settings.gradle") << """
            plugins {
                id "test-settings-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included-with-settings-plugin"))
        pluginBuilder.addSettingsPlugin("println 'test-settings-plugin applied to ' + settings.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("included-with-settings-plugin")
            includeBuild("build-src")
        """
        when:
        succeeds("help")

        then:
        outputContains("test-settings-plugin applied to :build-src")
    }

    @ToBeFixedForConfigurationCache(because="composite build")
    def "nested build can apply settings plugins contributed by other included builds"() {
        file("nested-parent/nested/settings.gradle") << """
            plugins {
                id "test-settings-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included-with-settings-plugin"))
        pluginBuilder.addSettingsPlugin("println 'test-settings-plugin applied to ' + settings.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("included-with-settings-plugin")
            includeBuild("nested-parent")
        """

        file("nested-parent/settings.gradle") << """
            includeBuild("nested")
        """

        when:
        succeeds("help")

        then:
        outputContains("test-settings-plugin applied to :nested")
    }

    // this documents the current behavior
    def "settings plugins in an included build with explicit substitution rules are not seen"() {
        file("build-src/settings.gradle") << """
            plugins {
                id "test-settings-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included-with-settings-plugin"))
        pluginBuilder.addSettingsPlugin("println 'test-settings-plugin applied to ' + settings.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("included-with-settings-plugin") {
                // Includes with substitution rules are not configured during initialization but on demand if explicitly depended on
                // See: IncludedBuildDependencySubstitutionsBuilder.build()
                dependencySubstitution {
                    substitute module('org.sample:my-plugin') with project(':')
                }
            }
            includeBuild("build-src")
        """
        when:
        fails("help")

        then:
        failure.assertHasDescription("Plugin [id: 'test-settings-plugin'] was not found in any of the following sources:")
    }
}
