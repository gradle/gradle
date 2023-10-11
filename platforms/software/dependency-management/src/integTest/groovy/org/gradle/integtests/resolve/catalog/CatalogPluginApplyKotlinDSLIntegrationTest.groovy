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

package org.gradle.integtests.resolve.catalog

import org.gradle.integtests.resolve.PluginDslSupport
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

@LeaksFileHandles("Kotlin Compiler Daemon working directory")
class CatalogPluginApplyKotlinDSLIntegrationTest extends AbstractVersionCatalogIntegrationTest implements PluginDslSupport {
    @Rule
    final MavenHttpPluginRepository pluginPortal = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def "can apply a plugin declared in a catalog"() {
        String taskName = 'greet'
        String message = 'Hello from plugin!'
        String pluginId = 'com.acme.greeter'
        String pluginVersion = '1.5'
        def plugin = new PluginBuilder(file("greeter"))
            .addPluginWithPrintlnTask(taskName, message, pluginId)
            .publishAs("some", "artifact", pluginVersion, pluginPortal, executer)

        // We use the Groovy DSL for settings because that's not what we want to
        // test and the setup would be more complicated with Kotlin
        settingsFile << """
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            plugin("$alias", "com.acme.greeter").version("1.5")
        }
    }
}"""
        buildFile.renameTo(file('fixture.gradle'))
        buildKotlinFile << """
            plugins {
                alias(libs.plugins.${alias.replace('-', '.')})
            }

            apply(from="fixture.gradle")
        """

        when:
        plugin.allowAll()
        succeeds taskName

        then:
        outputContains message

        where:
        alias << ['greeter', 'some.greeter', 'some-greeter']
    }
}
