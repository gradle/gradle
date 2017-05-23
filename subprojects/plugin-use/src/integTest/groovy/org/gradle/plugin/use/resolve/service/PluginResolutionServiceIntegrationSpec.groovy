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

package org.gradle.plugin.use.resolve.service

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.hamcrest.Matchers
import org.junit.Rule

class PluginResolutionServiceIntegrationSpec extends AbstractIntegrationSpec {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginResolutionServiceTestServer portal = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        portal.start()
    }

    @LeaksFileHandles
    def "plugin declared in plugins {} block gets resolved and applied"() {
        portal.expectPluginQuery("org.my.myplugin", "1.0", "my", "plugin", "1.0")
        publishPlugin("org.my.myplugin", "my", "plugin", "1.0")

        buildScript applyAndVerify("org.my.myplugin", "1.0")

        expect:
        succeeds("verify")
    }

    def "resolution fails if Gradle is in offline mode"() {
        buildScript applyAndVerify("org.my.myplugin", "1.0")
        args("--offline")

        expect:
        fails("verify")
        failure.assertHasDescription("Error resolving plugin [id 'org.my.myplugin' version '1.0']")
        failure.assertHasCause("Plugin cannot be resolved from $portal.apiAddress because Gradle is running in offline mode")
    }

    def "cannot resolve plugin with snapshot version"() {
        buildScript applyAndVerify("org.my.myplugin", "1.0-SNAPSHOT")

        expect:
        fails("verify")
        pluginNotFound("1.0-SNAPSHOT")
        resolutionServiceDetail("snapshot plugin versions are not supported")
    }

    def "cannot resolve plugin with dynamic version"() {
        buildScript applyAndVerify("org.my.myplugin", pluginVersion)

        expect:
        fails("verify")
        pluginNotFound(pluginVersion)
        resolutionServiceDetail("dynamic plugin versions are not supported")

        where:
        pluginVersion << ["[1.0,2.0)", "1.+", "latest.release"]
    }

    private void publishPlugin(String pluginId, String group, String artifact, String version) {
        def module = portal.m2repo.module(group, artifact, version) // don't know why tests are failing if this module is publish()'ed
        module.allowAll()
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", pluginId)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }

    private String applyAndVerify(String id, String version) {
        """
            plugins {
                id "$id" version "$version"
            }

            task verify {
                doLast {
                    assert pluginApplied
                }
            }
        """
    }

    void pluginNotFound(String version = "1.0") {
        failure.assertThatDescription(Matchers.startsWith("Plugin [id 'org.my.myplugin' version '$version'] was not found in any of the following sources:"))
    }

    void resolutionServiceDetail(String detail) {
        failure.assertThatDescription(Matchers.containsString("Gradle Central Plugin Repository ($detail)"))
    }
}
