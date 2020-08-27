/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.initialization.buildsrc

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder


class BuildSrcIncludedBuildIntegrationTest extends AbstractIntegrationSpec {
    def "buildSrc cannot (yet) define any included builds"() {
        file("buildSrc/settings.gradle") << """
            includeBuild "child"
        """
        file("buildSrc/child/settings.gradle").createFile()

        when:
        fails()

        then:
        failure.assertHasDescription("Cannot include build 'child' in build 'buildSrc'. This is not supported yet.")
    }

    def "buildSrc can apply plugins contributed by other included builds"() {
        file("buildSrc/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("included"))
        pluginBuilder.addPlugin("println 'test-plugin applied to ' + project.gradle.publicBuildPath.buildPath")
        pluginBuilder.prepareToExecute()

        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("help")
        then:
        outputContains("test-plugin applied to :buildSrc")
    }

    @NotYetImplemented
    def "plugins from buildSrc can be used by other included builds"() {
        file("included/build.gradle") << """
            plugins {
                id "test-plugin"
            }
        """

        def pluginBuilder = new PluginBuilder(file("buildSrc"))
        pluginBuilder.addPlugin("println 'test-plugin applied'")
        pluginBuilder.generateForBuildSrc()

        buildFile << """
            task executeIncluded {
                dependsOn gradle.includedBuild("included").task(":help")
            }
        """
        settingsFile << """
            includeBuild("included")
        """
        when:
        succeeds("executeIncluded")
        then:
        outputContains("test-plugin applied")
    }
}
