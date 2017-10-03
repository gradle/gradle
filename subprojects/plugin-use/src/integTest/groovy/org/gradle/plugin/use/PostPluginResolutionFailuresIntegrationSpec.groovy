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
import org.gradle.plugin.use.resolve.service.PluginResolutionServiceTestServer
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

import static org.hamcrest.Matchers.startsWith

@LeaksFileHandles
class PostPluginResolutionFailuresIntegrationSpec extends AbstractIntegrationSpec {
    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginResolutionServiceTestServer portal = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        portal.start()
    }

    def "error finding plugin by id"() {
        portal.expectPluginQuery("org.my.myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("otherid", "my", "plugin", "1.0")

        buildScript applyPlugin("org.my.myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("Could not apply requested plugin [id 'org.my.myplugin' version '1.0'] as it does not provide a plugin with id 'org.my.myplugin'"))
        failure.assertHasLineNumber(3)
    }

    def "error loading plugin"() {
        portal.expectPluginQuery("org.my.myplugin", "1.0", "my", "plugin", "1.0")
        publishUnloadablePlugin("org.my.myplugin", "my", "plugin", "1.0")

        buildScript applyPlugin("org.my.myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id 'org.my.myplugin' version '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not create plugin of type 'TestPlugin'.")
    }

    def "error creating plugin"() {
        portal.expectPluginQuery("org.my.myplugin", "1.0", "my", "plugin", "1.0")
        publishNonConstructablePlugin("org.my.myplugin", "my", "plugin", "1.0")

        buildScript applyPlugin("org.my.myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id 'org.my.myplugin' version '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not create plugin of type 'TestPlugin'.")
        failure.assertHasCause("broken plugin")
    }

    def "error applying plugin"() {
        portal.expectPluginQuery("org.my.myplugin", "1.0", "my", "plugin", "1.0")
        publishFailingPlugin("org.my.myplugin", "my", "plugin", "1.0")

        buildScript applyPlugin("org.my.myplugin", "1.0")

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id 'org.my.myplugin' version '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("throwing plugin")
    }

    private void publishPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private void publishUnloadablePlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version)
        module.allowAll()
        pluginBuilder.addUnloadablePlugin(pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private void publishNonConstructablePlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version)
        module.allowAll()
        pluginBuilder.addNonConstructablePlugin(pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private void publishFailingPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version)
        module.allowAll()
        pluginBuilder.addPlugin("throw new Exception('throwing plugin')", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private static String applyPlugin(String id, String version) {
        """
            plugins {
                id "$id" version "$version"
            }

            task verify // no-op, but gives us a task to execute
        """
    }
}
