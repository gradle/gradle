/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin.use

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.file.LeaksFileHandles

import static org.gradle.plugin.use.resolve.internal.NotNonCorePluginOnClasspathCheckPluginResolver.pluginOnClasspathErrorMessage

@LeaksFileHandles
class NonCorePluginAlreadyOnClasspathDetectionIntegrationSpec extends AbstractIntegrationSpec {

    private testPluginBuildscriptBlock() {
        return """
            buildscript {
                repositories {
                    maven { url "$mavenRepo.uri" }
                }
                dependencies {
                    classpath "plugin:plugin:1.0"
                }
            }
        """
    }

    private testPluginPluginsBlock() {
        return """
            plugins {
                id "plugin"
            }
        """
    }

    private publishTestPlugin() {
        def pluginBuilder = new PluginBuilder(testDirectory.file("plugin"))

        def module = mavenRepo.module("plugin", "plugin")
        def artifactFile = module.artifact([:]).artifactFile
        module.publish()

        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "plugin")
        pluginBuilder.publishTo(executer, artifactFile)
    }

    private publishBuildSrcTestPlugin() {
        def pluginBuilder = new PluginBuilder(file("buildSrc"))
        def message = "from plugin"
        def taskName = "pluginTask"
        pluginBuilder.addPluginWithPrintlnTask(taskName, message, "plugin")
        pluginBuilder.generateForBuildSrc()
    }

    def "cannot apply plugins added to parent buildscript classpath in plugins block"() {
        given:
        publishTestPlugin()

        when:
        buildScript """
            ${testPluginBuildscriptBlock()}
        """

        settingsFile << "include 'sub'"

        file("sub/build.gradle") << """
            ${testPluginPluginsBlock()}
        """

        then:
        fails "sub:tasks"

        and:
        failure.assertHasCause(pluginOnClasspathErrorMessage('plugin'))
    }

    def "cannot apply buildSrc plugins in plugins block"() {
        given:
        publishBuildSrcTestPlugin()

        when:
        buildScript """
            ${testPluginPluginsBlock()}
        """

        then:
        fails "tasks"

        and:
        failure.assertHasCause(pluginOnClasspathErrorMessage('plugin'))
    }

}
