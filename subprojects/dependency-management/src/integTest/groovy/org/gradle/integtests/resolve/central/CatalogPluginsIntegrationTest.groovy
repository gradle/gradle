/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.integtests.resolve.central

import org.gradle.integtests.resolve.PluginDslSupport
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule
import spock.lang.Issue

class CatalogPluginsIntegrationTest extends AbstractCentralDependenciesIntegrationTest implements PluginDslSupport {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        usePluginRepoMirror = false // otherwise the plugin portal fixture doesn't work!
    }

    @Issue("https://github.com/gradle/gradle/issues/16079")
    def "should use excludes for catalogs imported from files"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        file("dependencies1.toml") << """
[plugins]
com.acme.greeter = "1.7"
        """
        file("dependencies2.toml") << """
[plugins]
com.acme.greeter = "1.5"
        """

        file("settings.gradle") << """
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("dependencies1.toml")) {
                excludePlugin("com.acme.greeter")
            }
        }
        create("libs2") {
            from(files("dependencies2.toml"))
        }
    }
}"""
        withPlugin pluginId

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message
    }
}
