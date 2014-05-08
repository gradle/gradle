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

package org.gradle.integtests.resolve.portal

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.pluginportal.PluginPortalTestServer
import org.junit.Rule

public class PluginPortalClientIntegrationTest extends AbstractIntegrationSpec {

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    PluginPortalTestServer portal = new PluginPortalTestServer(executer, mavenRepo)

    def setup() {
        portal.start()
    }

    def "plugin declared in plugins {} block gets resolved from portal and applied"() {
        def module = portal.expectPluginQuery("myplugin", "1.0", "my", "plugin", "1.0")
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", "myplugin")
        pluginBuilder.publishTo(executer, module.artifactFile)
        module.allowAll()

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify << {
                assert pluginApplied
            }
        """

        expect:
        succeeds("verify")
    }

    def "404 response from plugin portal fails resolution"() {
        portal.expectMissing("myplugin", "1.0")

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify
        """

        expect:
        fails("verify")
        failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '1.0'] from plugin repositories")
    }

    def "portal JSON response with unknown implementation type"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "foo", "foo") {
            implementationType = "SUPER_GREAT"
        }

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify
        """

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    def "portal JSON response with missing repo"() {
        portal.expectPluginQuery("myplugin", "1.0", "foo", "bar", "1.0") // note: not publishing to returned module

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify
        """

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

    def "portal JSON response with invalid JSON syntax"() {
        portal.expectPluginQuery("myplugin", "1.0") {
            contentType = "application/json"
            writer.withWriter { it << "[}" }
        }

        buildScript """
            plugins {
                id "myplugin" version "1.0"
            }

            task verify
        """

        expect:
        fails("verify")
        //failure.assertHasCause("Cannot resolve plugin request [plugin: 'myplugin', version: '$pluginVersion'] from plugin repositories")
    }

}
