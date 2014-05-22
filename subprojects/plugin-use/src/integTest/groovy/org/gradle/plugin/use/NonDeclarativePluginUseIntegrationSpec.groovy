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
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.junit.Rule

class NonDeclarativePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"
    public static final String USE = "plugins { id '$PLUGIN_ID' version '$VERSION' }"

    @Rule
    PluginResolutionServiceTestServer service = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def pluginBuilder = new PluginBuilder(file("plugin"))

    def setup() {
        service.start()
    }

    def "non declarative plugin can depend on java plugin and access its api"() {
        given:
        publishPlugin """
            project.apply plugin: 'java'
                println getClass().classLoader.loadClass('org.gradle.api.plugins.JavaPlugin').name
            project.task('pluginName') << {
            }
        """

        when:
        buildScript USE

        then:
        succeeds("pluginName")
        output.contains("org.gradle.api.plugins.JavaPlugin")
    }


    void publishPlugin(String impl) {
        service.expectPluginQuery(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION) {
            legacy = true
        }
        def module = service.m2repo.module(GROUP, ARTIFACT, VERSION)
        module.allowAll()

        pluginBuilder.addPlugin(impl, PLUGIN_ID)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }
}
