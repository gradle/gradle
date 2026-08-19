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
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

@LeaksFileHandles
class RuleSourcePluginUseIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file(ARTIFACT))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "can apply a rule source only plugin via plugins container"() {
        given:
        pluginBuilder.with {
            addRuleSource(PLUGIN_ID)
            publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()
        }

        and:
        buildFile """
            plugins { id '$PLUGIN_ID' version '$VERSION' }
        """

        expect:
        expectSoftwareModelDeprecations("org.myplugin")
        succeeds("fromModelRule")
        output.contains("Model rule provided task executed")
    }

    private void expectSoftwareModelDeprecations(String... pluginNames) {
        for (String name : pluginNames) {
            executer.expectDocumentedDeprecationWarning("The ${name} plugin has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
        }
    }
}
