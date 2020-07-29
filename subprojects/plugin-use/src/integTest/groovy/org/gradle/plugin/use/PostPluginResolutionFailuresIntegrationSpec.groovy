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

import static org.hamcrest.CoreMatchers.startsWith

@LeaksFileHandles
class PostPluginResolutionFailuresIntegrationSpec extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.my.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        executer.requireOwnGradleUserHomeDir()
    }

    def "error loading plugin"() {
        pluginBuilder.addUnloadablePlugin(PLUGIN_ID)
        pluginBuilder.publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()

        buildScript applyPlugin()

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id: 'org.my.myplugin', version: '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not create plugin of type 'TestPlugin'.")
    }

    def "error creating plugin"() {
        pluginBuilder.addNonConstructiblePlugin(PLUGIN_ID)
        pluginBuilder.publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()

        buildScript applyPlugin()

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id: 'org.my.myplugin', version: '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("Could not create plugin of type 'TestPlugin'.")
        failure.assertHasCause("broken plugin")
    }

    def "error applying plugin"() {
        pluginBuilder.addPlugin("throw new Exception('throwing plugin')", PLUGIN_ID)
        pluginBuilder.publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer).allowAll()

        buildScript applyPlugin()

        expect:
        fails("verify")
        failure.assertThatDescription(startsWith("An exception occurred applying plugin request [id: 'org.my.myplugin', version: '1.0']"))
        failure.assertHasLineNumber(3)
        failure.assertHasCause("throwing plugin")
    }

    private static String applyPlugin() {
        """
            plugins {
                id "$PLUGIN_ID" version "$VERSION"
            }

            task verify // no-op, but gives us a task to execute
        """
    }
}
