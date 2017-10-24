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
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.startsWith

class PluginResolutionServiceIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.my.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file(ARTIFACT))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    @LeaksFileHandles
    def "plugin declared in plugins {} block gets resolved and applied"() {
        publishPlugin()

        buildScript applyAndVerify(PLUGIN_ID, VERSION)

        expect:
        succeeds("verify")
    }

    def "resolution fails if Gradle is in offline mode"() {
        publishPlugin()
        buildScript applyAndVerify(PLUGIN_ID, VERSION)
        args("--offline")

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("Plugin [id: 'org.my.myplugin', version: '1.0'] was not found"))
    }

    def "cannot resolve plugin with snapshot version"() {
        buildScript applyAndVerify(PLUGIN_ID, "1.0-SNAPSHOT")

        expect:
        fails("verify")
        pluginNotFound("1.0-SNAPSHOT")
        resolutionServiceDetail("snapshot plugin versions are not supported")
    }

    def "cannot resolve plugin with dynamic version"() {
        buildScript applyAndVerify(PLUGIN_ID, pluginVersion)

        expect:
        fails("verify")
        pluginNotFound(pluginVersion)
        resolutionServiceDetail("dynamic plugin versions are not supported")

        where:
        pluginVersion << ["[1.0,2.0)", "1.+", "latest.release"]
    }

    private void publishPlugin() {
        pluginBuilder.with {
            addPlugin("project.ext.pluginApplied = true", PLUGIN_ID)
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()
        }
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
        failure.assertThatDescription(startsWith("Plugin [id: 'org.my.myplugin', version: '$version'] was not found in any of the following sources:"))
    }

    void resolutionServiceDetail(String detail) {
        failure.assertThatDescription(containsString("Gradle Central Plugin Repository ($detail)"))
    }
}
