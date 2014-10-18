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
package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.TestPlugin1
import org.gradle.api.internal.project.TestPlugin2
import org.gradle.api.internal.project.TestRuleSource
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

public class DefaultPluginContainerTest extends Specification {

    def PluginInspector pluginInspector = new PluginInspector(new ModelRuleSourceDetector())
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    def pluginRegistry = new DefaultPluginRegistry(pluginInspector, classLoader)
    def applicator = Mock(PluginApplicator)
    def instantiator = new DirectInstantiator()

    @Subject
    def container = new DefaultPluginContainer(pluginRegistry, instantiator, applicator)

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        classLoader.addURL(testDirectoryProvider.testDirectory.toURL())
        testDirectoryProvider.file("META-INF/gradle-plugins/plugin.properties") << "implementation-class=${TestPlugin1.name}"
    }

    def "offers plugin management via plugin id"() {
        when:
        def p = container.apply(TestPlugin1)

        then:
        p.is(container.apply("plugin"))
        p.is(container.apply(TestPlugin1))

        p.is(container.findPlugin(TestPlugin1))
        p.is(container.findPlugin("plugin"))

        !container.findPlugin(UnknownPlugin)
        !container.findPlugin("unknown")

        container.hasPlugin("plugin")
        container.hasPlugin(TestPlugin1)

        !container.hasPlugin("unknown")
        !container.hasPlugin(UnknownPlugin)
    }

    private class UnknownPlugin implements Plugin<Project> {
        void apply(Project project) {}
    }

    def "offers plugin management via plugin type"() {
        when:
        def p = container.apply(TestPlugin1)

        then:
        p.is(container.apply(TestPlugin1))
        p.is(container.findPlugin(TestPlugin1))
        container.hasPlugin(TestPlugin1)

        !p.is(container.findPlugin(TestPlugin2))
        !container.hasPlugin(TestPlugin2)
    }

    def "does not find plugin by unknown id"() {
        expect:
        !container.hasPlugin("x")
        !container.findPlugin("x")
    }

    def "fails when getting unknown plugin"() {
        when:
        container.getPlugin("unknown")

        then:
        thrown(UnknownPluginException)
    }

    def "fails when getting plugin of unknown type"() {
        when:
        container.getPlugin(TestPlugin1)

        then:
        thrown(UnknownPluginException)
    }

    def "executes action for plugin with given id"() {
        def plugin = new TestPlugin1()
        def plugins = []
        container.add(plugin)

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins == [plugin]
    }

    def "executes action when plugin with given id is added later"() {
        given:
        def groovyLoader = new GroovyClassLoader(getClass().classLoader)
        def classPathAdditions = testDirectoryProvider.createDir("resources")
        groovyLoader.addURL(classPathAdditions.toURL())

        def pluginClass = groovyLoader.parseClass """
            package test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class TestPlugin implements Plugin<Project> {
                void apply(Project project) {

                }
            }
        """
        classPathAdditions.file("META-INF/gradle-plugins/plugin.properties") << "implementation-class=${pluginClass.name}"

        def pluginRegistry = new DefaultPluginRegistry(pluginInspector, groovyLoader)
        def container = new DefaultPluginContainer(pluginRegistry, new DirectInstantiator(), applicator)
        def plugin = pluginClass.newInstance()
        def plugins = []

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins.empty

        when:
        container.add(plugin)

        then:
        plugins == [plugin]
    }

    def "executes action when plugin with given id, of plugin not in registry, is added later"() {
        given:
        def groovyLoader = new GroovyClassLoader(getClass().classLoader)
        def classPathAdditions = testDirectoryProvider.createDir("resources")
        groovyLoader.addURL(classPathAdditions.toURL())

        def pluginClass = groovyLoader.parseClass """
            package test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class TestPlugin implements Plugin<Project> {
                void apply(Project project) {

                }
            }
        """
        classPathAdditions.file("META-INF/gradle-plugins/plugin.properties") << "implementation-class=${pluginClass.name}"

        def pluginRegistry = new DefaultPluginRegistry(pluginInspector, groovyLoader.parent)
        def container = new DefaultPluginContainer(pluginRegistry, new DirectInstantiator(), applicator)
        def plugin = pluginClass.newInstance()
        def plugins = []

        when:
        container.apply("plugin")

        then:
        thrown UnknownPluginException

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins.empty

        when:
        container.add(plugin)

        then:
        plugins == [plugin]
    }

    def "no error when withId used and plugin with no id"() {
        given:
        def groovyLoader = new GroovyClassLoader(getClass().classLoader)

        def pluginClass = groovyLoader.parseClass """
            package test

            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class TestPlugin implements Plugin<Project> {
                void apply(Project project) {

                }
            }
        """

        def pluginRegistry = new DefaultPluginRegistry(pluginInspector, groovyLoader.parent)
        def container = new DefaultPluginContainer(pluginRegistry, new DirectInstantiator(), applicator)
        def plugin = pluginClass.newInstance()
        def plugins = []

        when:
        container.apply("plugin")

        then:
        thrown UnknownPluginException

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins.empty

        when:
        container.add(plugin)

        then:
        plugins == []
    }

    def "calls applicator for type only"() {
        when:
        container.apply(TestPlugin1)

        then:
        1 * applicator.applyImperative(null, { it instanceof TestPlugin1 })
    }

    def "calls applicator for id"() {
        when:
        container.apply("plugin")

        then:
        1 * applicator.applyImperative("plugin", { it instanceof TestPlugin1 })
    }

    def "a useful error message is set when a plain rule source type is passed to withType"() {
        when:
        container.withType(TestRuleSource)

        then:
        IllegalArgumentException e = thrown()
        e.message == "'$TestRuleSource.name' does not implement the Plugin interface."
    }

    def "a useful error message is set when an id for plain rule source type is passed to withId"() {
        given:
        testDirectoryProvider.file("META-INF/gradle-plugins/custom-rule-source.properties") << "implementation-class=${TestRuleSource.name}"

        when:
        container.withId("custom-rule-source") {}

        then:
        IllegalArgumentException e = thrown()
        e.message == "The type for id 'custom-rule-source' (class: '$TestRuleSource.name') is not a plugin implementing the Plugin interface. Please use AppliedPlugins.withPlugin() instead to detect it."
    }
}
