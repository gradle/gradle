/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

class FilteringPluginsByIdIntegrationTest extends AbstractIntegrationSpec {

    def "filters plugins by id"() {
        buildFile << """
            def operations = []
            plugins.withId("java") {
                operations << 'withId for ' + it.class.simpleName
            }
            operations << "applying"
            apply plugin: 'java'
            operations << "applied"

            task verify << { assert operations == ['applying', 'withId for JavaPlugin', 'applied'] }
        """

        expect:
        run("verify")
    }

    def "filters plugins by id when descriptor not on registry classpath"() {
        def pluginBuilder = new PluginBuilder(testDirectory)
        pluginBuilder.addPlugin("")
        pluginBuilder.publishTo(executer, file("plugin.jar"))

        buildFile << """
            def operations = []

            def loader = new URLClassLoader([file("plugin.jar").toURL()] as URL[], getClass().classLoader)
            def pluginClass = loader.loadClass("${pluginBuilder.packageName}.TestPlugin")

            plugins.withType(pluginClass) {
                operations << 'withType'
            }

            plugins.withId("test-plugin") {
                operations << 'withId'
            }

            operations << "applying"
            apply plugin: pluginClass
            operations << "applied"

            task verify << { assert operations == ['applying', 'withType', 'withId', 'applied'] }
        """

        expect:
        run("verify")
    }
}
