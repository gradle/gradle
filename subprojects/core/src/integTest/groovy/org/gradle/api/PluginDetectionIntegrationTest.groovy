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
package org.gradle.api

import org.gradle.api.plugins.AppliedPlugin
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.plugin.PluginBuilder
import spock.lang.Issue

/**
 * Tests various aspects of detecting the existence of plugins by their ID.
 */
class PluginDetectionIntegrationTest extends AbstractIntegrationSpec {

    public static final List<String> JAVA_PLUGIN_IDS = ["java", "org.gradle.java"]

    def "core plugins are detectable - applied by #appliedBy, detected by #detectedBy"() {
        buildFile << """
            def operations = []
            plugins.withId("$detectedBy") {
                operations << 'withId for ' + it.class.simpleName
            }
            pluginManager.withPlugin("$detectedBy") {
                // assert we are using our closure decoration and not closure coercion
                assert delegate instanceof $AppliedPlugin.name
                operations << 'withPlugin'
            }
            operations << "applying"
            apply plugin: '$appliedBy'
            operations << "applied"

            assert plugins["$detectedBy"]
            assert plugins.getPlugin("$detectedBy")
            assert pluginManager.hasPlugin("$detectedBy")
            assert pluginManager.findPlugin("$detectedBy").id == "$detectedBy"

            task verify {
                doLast {
                    assert operations[0] == 'applying'
                    assert operations[1] =~ /withId for JavaPlugin[\$]Inject[\\d]*/
                    assert operations[2] == 'withPlugin'
                    assert operations[3] == 'applied'
                }
            }
        """

        expect:
        run("verify")

        where:
        appliedBy << JAVA_PLUGIN_IDS * 2
        detectedBy << JAVA_PLUGIN_IDS + JAVA_PLUGIN_IDS.reverse()
    }

    @ToBeFixedForConfigurationCache(because = "Gradle.buildFinished")
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
                // assert we are using our closure decoration and not closure coercion
                assert delegate instanceof $AppliedPlugin.name
                operations << 'withPlugin'
            }

            operations << "applying"
            apply plugin: pluginClass
            apply type: ruleSourceClass
            operations << "applied"

            task verify { doLast { assert operations == ['applying', 'withType', 'withId', 'withPlugin', 'applied'] } }

            gradle.buildFinished { loader.close() }
        """

        expect:
        run("verify")
    }

    def "plugin manager with id is fired after the plugin is applied for imperative plugins"() {
        when:
        buildFile """
            pluginManager.withPlugin("java") {
              assert tasks.jar
            }

            apply plugin: "java"
        """

        then:
        succeeds "help"
    }

    def "plugin manager with id is fired after the plugin is applied for hybrid plugins"() {
        when:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Task
            import org.gradle.model.*

            class MyPlugin implements Plugin {
                void apply(project) {
                  project.tasks.create("imperative-sentinel")
                }

                static class Rules extends RuleSource {
                    @Model String thing() {
                        "foo"
                    }
                }
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my.properties") << "implementation-class=MyPlugin"

        buildFile """
            import org.gradle.model.internal.core.ModelPath

            pluginManager.withPlugin("my") {
              assert tasks."imperative-sentinel"
              // note: modelRegistry property is internal on project
              assert modelRegistry.node(ModelPath.path("thing")) != null
            }

            pluginManager.apply(MyPlugin)
        """

        then:
        succeeds "help"
    }

    def "plugin manager with id is fired after the plugin is applied for rule plugins"() {
        when:
        file("buildSrc/src/main/groovy/MyPlugin.groovy") << """
            import org.gradle.model.*

            class Rules extends RuleSource {
                @Model String thing() {
                    "foo"
                }
            }
        """

        file("buildSrc/src/main/resources/META-INF/gradle-plugins/my.properties") << "implementation-class=Rules"

        buildFile """
            import org.gradle.model.internal.core.ModelPath

            pluginManager.withPlugin("my") {
              // note: modelRegistry property is internal on project
              assert modelRegistry.node(ModelPath.path("thing")) != null
            }

            pluginManager.apply("my")
        """

        then:
        succeeds "help"
    }

    @Issue("http://discuss.gradle.org/t/concurrentmodification-exception-on-java-8-for-plugins-withid-with-gradle-2-4/8928")
    def "can nest detection"() {
        // Actual plugins in use here are insignificant
        when:
        file("buildSrc/src/main/groovy/PluginA.groovy") << """
            class PluginA implements $Plugin.name {
                void apply(project) {}
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/a.properties") << "implementation-class=PluginA"

        file("buildSrc/src/main/groovy/PluginB.groovy") << """
            class PluginB implements $Plugin.name {
                void apply(project) {}
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/b.properties") << "implementation-class=PluginB"

        file("buildSrc/src/main/groovy/PluginC.groovy") << """
            class PluginC implements $Plugin.name {
                void apply(project) {}
            }
        """
        file("buildSrc/src/main/resources/META-INF/gradle-plugins/c.properties") << "implementation-class=PluginC"

        buildScript """
            class ExamplePlugin implements Plugin<Project> {
                void apply(final Project project) {
                    project.plugins.withId('a') {
                        project.plugins.hasPlugin('b')
                    }
                    project.plugins.withId('c') {
                        project.plugins.hasPlugin('b')
                    }
                }
            }

            apply plugin: ExamplePlugin
            apply plugin: "a"
        """

        then:
        succeeds "help"
    }

}
