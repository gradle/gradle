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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.junit.Rule

@LeaksFileHandles
class RuleSourcePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"
    def pluginBuilder = new PluginBuilder(file(ARTIFACT))

    @Rule
    PluginResolutionServiceTestServer resolutionService = new PluginResolutionServiceTestServer(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        resolutionService.start()
    }

    def "can apply a rule source only plugin via plugins container"() {
        publishRuleSourcePlugin()

        buildScript """
            plugins { id '$PLUGIN_ID' version '$VERSION' }
        """

        expect:
        succeeds("fromModelRule")
        output.contains("Model rule provided task executed")
    }

    void publishRuleSourcePlugin() {
        resolutionService.expectPluginQuery(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION)
        def module = resolutionService.m2repo.module(GROUP, ARTIFACT, VERSION)
        module.allowAll()

        pluginBuilder.addRuleSource(PLUGIN_ID)
        pluginBuilder.publishTo(executer, module.artifactFile)
    }
}
