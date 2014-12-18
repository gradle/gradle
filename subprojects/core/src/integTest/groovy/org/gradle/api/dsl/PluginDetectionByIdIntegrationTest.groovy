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
import spock.lang.Unroll

/**
 * Tests various aspects of detecting the existence of plugins by their ID.
 */
class PluginDetectionByIdIntegrationTest extends AbstractIntegrationSpec {

    public static final List<String> JAVA_PLUGIN_IDS = ["java", "org.gradle.java"]

    @Unroll
    def "core plugins are detectable - applied by #appliedBy, detected by #detectedBy"() {
        buildFile << """
            def operations = []
            plugins.withId("$detectedBy") {
                operations << 'withId for ' + it.class.simpleName
            }
            pluginManager.withPlugin("$detectedBy") {
                operations << 'withPlugin'
            }
            operations << "applying"
            apply plugin: '$appliedBy'
            operations << "applied"

            assert plugins["$detectedBy"]
            assert plugins.getPlugin("$detectedBy")
            assert pluginManager.hasPlugin("$detectedBy")
            assert pluginManager.findPlugin("$detectedBy").id == "org.gradle.java"
            assert pluginManager.findPlugin("$detectedBy").namespace == "org.gradle"
            assert pluginManager.findPlugin("$detectedBy").name == "java"

            task verify << { assert operations == ['applying', 'withPlugin', 'withId for JavaPlugin', 'applied'] }
        """

        expect:
        run("verify")

        where:
        appliedBy << JAVA_PLUGIN_IDS * 2
        detectedBy << JAVA_PLUGIN_IDS + JAVA_PLUGIN_IDS.reverse()
    }

    def "unqualified ids from classpath are detectable"() {
        def pluginBuilder = new PluginBuilder(testDirectory)
        pluginBuilder.addPlugin("")
        pluginBuilder.addRuleSource("test-rule-source")
        pluginBuilder.publishTo(executer, file("plugin.jar"))

        buildFile << """
            def operations = []

            def loader = new URLClassLoader([file("plugin.jar").toURL()] as URL[], getClass().classLoader)
            def pluginClass = loader.loadClass("${pluginBuilder.packageName}.TestPlugin")
            def ruleSourceClass = loader.loadClass("${pluginBuilder.packageName}.TestRuleSource")

            plugins.withType(pluginClass) {
                operations << 'withType'
            }

            plugins.withId("test-plugin") {
                operations << 'withId'
            }

            pluginManager.withPlugin("test-rule-source") {
                operations << 'withPlugin'
            }

            operations << "applying"
            apply plugin: pluginClass
            apply type: ruleSourceClass
            operations << "applied"

            task verify << { assert operations == ['applying', 'withType', 'withId', 'withPlugin', 'applied'] }
        """

        expect:
        run("verify")
    }
}
