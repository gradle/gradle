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

import groovy.transform.NotYetImplemented
import org.gradle.api.Project
import org.gradle.api.specs.AndSpec
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.plugin.use.resolve.portal.PluginPortalTestServer
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

class PluginUseClassVisibilityIntegrationSpec extends AbstractIntegrationSpec {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginPortalTestServer portal = new PluginPortalTestServer(executer, mavenRepo)

    def setup() {
        executer.requireGradleHome() // need accurate classloading
        portal.start()
    }

    @NotYetImplemented
    def "plugin is available via `plugins` container"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify << {
                def foundById = false
                plugins.withId("myplugin") { foundById = true }
                assert foundById

                def foundByClass = false
                plugins.withClass(org.gradle.test.TestPlugin) { foundByClass = true }
                assert foundByClass
            }
        """

        expect:
        succeeds("verify")
    }

    def "plugin class isn't visible to build script"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("myplugin", "my", "plugin", "1.0")

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify << {
                try {
                    getClass().getClassLoader().loadClass("org.gradle.test.TestPlugin")
                    throw new AssertionError("plugin class *is* visible to build script")
                } catch (ClassNotFoundException expected) {}
            }
        """

        expect:
        succeeds("verify")
    }

    def "plugin can access Gradle API classes"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPluginThatAccessesGradleApiClasses("myplugin", "my", "plugin", "1.0")

        buildScript applyPlugin("myplugin", "1.0")

        expect:
        succeeds("verify")
    }

    def "plugin cannot access core Gradle plugin classes"() {
        portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        publishPluginThatAccessesCorePluginClasses("myplugin", "my", "plugin", "1.0")

        buildScript applyPlugin("myplugin", "1.0")

        expect:
        fails("verify")
    }

    private static String applyPlugin(String id, String version) {
        """
            plugins {
                id "$id" version "$version"
            }

            task verify // no-op, but gives us a task to execute
        """
    }

    private void publishPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private void publishPluginThatAccessesGradleApiClasses(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version)
        module.allowAll()
        pluginBuilder.addPlugin("assert project instanceof ${Project.name}; new ${AndSpec.name}()", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private void publishPluginThatAccessesCorePluginClasses(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version)
        module.allowAll()
        // why the heck does this fail with: java.lang.ClassCastException: java.util.LinkedHashMap cannot be cast to org.gradle.api.Project
        // pluginBuilder.addPlugin("apply plugin: ${JavaPlugin.name}", pluginId)
        pluginBuilder.addPlugin("getClass().getClassLoader().loadClass('org.gradle.api.plugins.JavaPlugin')", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }
}
