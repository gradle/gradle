/*
 * Copyright 2013 the original author or authors.
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

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.startsWith

@LeaksFileHandles
class PluginResolutionCachingIntegrationTest extends AbstractIntegrationSpec {

    public static final String PLUGIN_ID = "org.my.myplugin"
    public static final String VERSION = "1.0"
    public static final String GROUP = "my"
    public static final String ARTIFACT = "plugin"

    def pluginBuilder = new PluginBuilder(file("plugin"))

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    PluginBuilder.PluginHttpPublicationResults publication

    def setup() {
        executer.requireOwnGradleUserHomeDir()
        publication = publishPlugin()
        buildScript """
            plugins { id '$PLUGIN_ID' version '$VERSION' }
            task pluginApplied {
                doLast {
                    assert project.pluginApplied
                }
            }
        """
    }

    def "successful plugin resolution is cached"() {
        expect:
        pluginResolution()
        build()

        reset()
        build()
    }

    def "--refresh-dependencies invalidates cache"() {
        expect:
        pluginResolution()
        build()

        reset()
        args "--refresh-dependencies"
        cachedPluginResolution()
        build()

        reset()
        args() // clear --refresh-dependencies
        build()
    }

    def "can use --refresh-dependencies on first run"() {
        expect:
        args "--refresh-dependencies"
        pluginResolution()
        build()

        reset()
        build()
    }

    def "not found plugin is not cached"() {
        expect:
        pluginQueryNotFound()
        failPluginNotFound()

        reset()
        pluginResolution()
        build()

        reset()
        build()
    }

    def "error response is not cached"() {
        expect:
        pluginQueryError()
        failError()

        reset()
        pluginResolution()
        build()

        reset()
        build()
    }

    def "unexpected response is not cached"() {
        expect:
        pluginQueryUnexpectedResponse()
        failError()

        reset()
        pluginResolution()
        build()

        reset()
        build()
    }

    def "--offline can be used if response is cached"() {
        expect:
        pluginResolution()
        build()

        reset()
        args "--offline"
        build()
    }

    void reset() {
        pluginRepo.server.resetExpectations()
    }

    void build() {
        run "pluginApplied"
    }

    void failPluginNotFound() {
        fails "tasks"
        failure.assertThatDescription(startsWith("Plugin [id: 'org.my.myplugin', version: '1.0'] was not found"))
    }

    void failError() {
        fails "tasks"
        failure.assertThatDescription(startsWith("Plugin [id: 'org.my.myplugin', version: '1.0'] was not found"))
        failure.assertThatDescription(containsString("Gradle Central Plugin Repository (Could not resolve plugin artifact 'org.my.myplugin:org.my.myplugin.gradle.plugin:1.0')"))
    }

    PluginBuilder.PluginHttpPublicationResults publishPlugin() {
        pluginBuilder.addPlugin("project.ext.pluginApplied = true", PLUGIN_ID)
        return pluginBuilder.publishAs(GROUP, ARTIFACT, VERSION, pluginRepo, executer)
    }

    void pluginResolution() {
        pluginRepo.expectPluginResolution(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION)
    }

    void cachedPluginResolution() {
        pluginRepo.expectCachedPluginResolution(PLUGIN_ID, VERSION, GROUP, ARTIFACT, VERSION)
    }

    void pluginQueryNotFound() {
        pluginRepo.expectPluginMarkerMissing(PLUGIN_ID, VERSION)
    }

    void pluginQueryError() {
        pluginRepo.expectPluginMarkerBroken(PLUGIN_ID, VERSION)
    }

    void pluginQueryUnexpectedResponse() {
        pluginRepo.expectPluginMarkerQuery(PLUGIN_ID, VERSION) {
            writer.withWriter {
                it << "foo"
            }
        }
    }

}
