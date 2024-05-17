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
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.TestRuleSource
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.operations.TestBuildOperationExecutor
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.CustomPluginWithInjection
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class DefaultPluginContainerTest extends Specification {

    PluginInspector pluginInspector = new PluginInspector(new ModelRuleSourceDetector())
    def classLoader = new GroovyClassLoader(getClass().classLoader)
    def pluginRegistry = new DefaultPluginRegistry(pluginInspector, scope(classLoader))
    def target = Mock(PluginTarget)
    def instantiator = TestUtil.instantiatorFactory().inject()
    def pluginManager = new DefaultPluginManager(pluginRegistry, instantiator, target, new TestBuildOperationExecutor(), new DefaultUserCodeApplicationContext(), CollectionCallbackActionDecorator.NOOP, TestUtil.domainObjectCollectionFactory())

    @Subject
    def container = pluginManager.pluginContainer

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    private Class<?> plugin1Class = classLoader.parseClass("""
        import org.gradle.api.Plugin
        import org.gradle.api.Project
        class TestPlugin1 implements Plugin<Project> {
          void apply(Project project) {}
        }
    """)

    private Class<?> plugin2Class = classLoader.parseClass("""
        import org.gradle.api.Plugin
        import org.gradle.api.Project
        class TestPlugin2 implements Plugin<Project> {
          void apply(Project project) {}
        }
    """)

    def setup() {
        classLoader.addURL(testDirectoryProvider.testDirectory.toURI().toURL())
        testDirectoryProvider.file("META-INF/gradle-plugins/plugin.properties") << "implementation-class=${plugin1Class.name}"
    }

    def "offers plugin management via plugin id"() {
        when:
        def plugin = container.apply(plugin1Class)

        then:
        plugin.is(container.apply("plugin"))
        plugin.is(container.apply(plugin1Class))

        plugin.is(container.findPlugin(plugin1Class))
        plugin.is(container.findPlugin("plugin"))

        !container.findPlugin(UnknownPlugin)
        !container.findPlugin("unknown")

        container.hasPlugin("plugin")
        container.hasPlugin(plugin1Class)

        !container.hasPlugin("unknown")
        !container.hasPlugin(UnknownPlugin)
    }

    private class UnknownPlugin implements Plugin<Project> {
        void apply(Project project) {}
    }

    def "offers plugin management via plugin type"() {
        when:
        def plugin = container.apply(plugin1Class)

        then:
        plugin.is(container.apply(plugin1Class))
        plugin.is(container.findPlugin(plugin1Class))
        container.hasPlugin(plugin1Class)

        !plugin.is(container.findPlugin(plugin2Class))
        !container.hasPlugin(plugin2Class)
    }

    def "offers plugin management via injectable plugin type "() {
        when:
        def plugin = container.apply(CustomPluginWithInjection)

        then:
        container.hasPlugin(CustomPluginWithInjection)
        container.getPlugin(CustomPluginWithInjection) == plugin
        container.findPlugin(CustomPluginWithInjection) == plugin
    }

    def "id-based injectable plugins can be found by their id"() {
        when:
        def plugin = container.apply("custom-plugin-with-injection")
        def executed = false

        then:

        container.withId("custom-plugin-with-injection", {
            assert it == plugin
            executed = true
        })
        executed
        container.getPlugin("custom-plugin-with-injection") == plugin
        container.hasPlugin("custom-plugin-with-injection")
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
        container.getPlugin(plugin1Class)

        then:
        thrown(UnknownPluginException)
    }

    def "executes action for plugin with given id"() {
        def plugins = []
        container.apply(plugin1Class)

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins[0].class == plugin1Class
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

        def plugins = []

        when:
        container.withId("plugin") { plugins << it }

        then:
        plugins.empty

        when:
        container.apply(pluginClass)

        then:
        plugins[0].class == pluginClass
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
        classPathAdditions.file("META-INF/gradle-plugins/plugin2.properties") << "implementation-class=${pluginClass.name}"

        def plugins = []

        when:
        container.apply("plugin2")

        then:
        thrown UnknownPluginException

        when:
        container.withId("plugin2") { plugins << it }

        then:
        plugins.empty

        when:
        container.apply(pluginClass)

        then:
        plugins[0].class == pluginClass
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

        def plugins = []

        when:
        container.apply("plugin2")

        then:
        thrown UnknownPluginException

        when:
        container.withId("plugin2") { plugins << it }

        then:
        plugins.empty

        when:
        container.apply(pluginClass)

        then:
        plugins == []
    }

    def "calls apply on target for type only"() {
        when:
        container.apply(plugin1Class)

        then:
        1 * target.applyImperative(null, { plugin1Class.isInstance(it) })
    }

    def "calls apply on target for id"() {
        when:
        container.apply("plugin")

        then:
        1 * target.applyImperative("plugin", { plugin1Class.isInstance(it) })
    }

    def "a useful error message is set when a plain rule source type is passed to withType"() {
        when:
        container.withType(TestRuleSource)

        then:
        IllegalArgumentException e = thrown()
        e.message == "'$TestRuleSource.name' does not implement the Plugin interface."
    }

    def "cannot add plugins directly to container"() {
        def plugin = Mock(Plugin)

        when:
        container.add(plugin)

        then:
        thrown UnsupportedOperationException

        when:
        container.withType(plugin.class).add(plugin)

        then:
        thrown UnsupportedOperationException

        when:
        container.addAll([plugin])

        then:
        thrown UnsupportedOperationException

        when:
        container.withType(plugin.class).addAll([plugin])

        then:
        thrown UnsupportedOperationException
    }

    def "cannot remove plugins from container"() {
        def plugin = Mock(Plugin)

        when:
        container.remove(plugin)

        then:
        thrown UnsupportedOperationException

        when:
        container.withType(plugin.class).remove(plugin)

        then:
        thrown UnsupportedOperationException

        when:
        container.removeAll([plugin])

        then:
        thrown UnsupportedOperationException

        when:
        container.withType(plugin.class).removeAll([plugin])

        then:
        thrown UnsupportedOperationException

        when:
        container.clear()

        then:
        thrown UnsupportedOperationException

        when:
        container.withType(plugin.class).clear()

        then:
        thrown UnsupportedOperationException
    }

    def scope(ClassLoader classLoader) {
        return Stub(ClassLoaderScope) {
            getLocalClassLoader() >> classLoader
        }
    }
}
