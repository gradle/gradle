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

package org.gradle.api.internal.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.InvalidPluginException
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.RuleSource
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class PluginManagerTest extends Specification {

    def classLoader = new GroovyClassLoader(getClass().classLoader)
    def registry = new DefaultPluginRegistry(new PluginInspector(new ModelRuleSourceDetector()), classLoader)
    def applicator = Mock(PluginApplicator)
    def manager = new PluginManager(registry, new DirectInstantiator(), applicator)

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        classLoader.addURL(testDirectoryProvider.testDirectory.toURI().toURL())
    }

    def "empty manager ops"() {
        expect:
        manager.pluginContainer.isEmpty()
        !manager.appliedPlugins.contains("foo")
    }

    @RuleSource
    static class Rules {

    }

    def "can apply rules plugin with no id"() {
        when:
        manager.apply(Rules)

        then:
        1 * applicator.applyRules(null, Rules)

        and:
        manager.pluginContainer.isEmpty()
    }

    def "can apply rules plugin by class with id"() {
        given:
        addPluginId("foo", Rules)

        when:
        manager.apply(Rules)

        then:
        1 * applicator.applyRules(null, Rules)

        and:
        manager.pluginContainer.isEmpty()
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply rules plugin by id"() {
        given:
        addPluginId("foo", Rules)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyRules("foo", Rules)

        and:
        manager.pluginContainer.isEmpty()
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    static class Hybrid implements Plugin<Project> {
        @Override
        void apply(Project target) {

        }

        @RuleSource
        static class Rules {}
    }

    def "can apply hybrid plugin with no id"() {
        when:
        manager.apply(Hybrid)

        then:
        1 * applicator.applyImperativeRulesHybrid(null, { it instanceof Hybrid })

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply hybrid plugin by class with id"() {
        given:
        addPluginId("foo", Hybrid)

        when:
        manager.apply(Hybrid)

        then:
        1 * applicator.applyImperativeRulesHybrid(null, { it instanceof Hybrid })

        and:
        manager.pluginContainer.size() == 1
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply hybrid plugin by id"() {
        given:
        addPluginId("foo", Hybrid)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyImperativeRulesHybrid("foo", { it instanceof Hybrid })

        and:
        manager.pluginContainer.size() == 1
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }


    static class Imperative implements Plugin<Project> {
        @Override
        void apply(Project target) {

        }
    }

    def "can apply imperative plugin with no id"() {
        when:
        manager.apply(Imperative)

        then:
        1 * applicator.applyImperative(null, { it instanceof Imperative })

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply imperative plugin by class with id"() {
        given:
        addPluginId("foo", Imperative)

        when:
        manager.apply(Imperative)

        then:
        1 * applicator.applyImperative(null, { it instanceof Imperative })

        and:
        manager.pluginContainer.size() == 1
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply imperative plugin by id"() {
        given:
        addPluginId("foo", Imperative)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyImperative("foo", { it instanceof Imperative })

        and:
        manager.pluginContainer.size() == 1
        manager.appliedPlugins.contains("foo")
        def called = false
        manager.appliedPlugins.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "does not allow duplicate plugins"() {
        given:
        def applied = []
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

        when:
        addPluginId("plugin", Imperative)
        manager.appliedPlugins.withPlugin("plugin") { applied << it }

        then:
        applied.empty

        when:
        def dir = testDirectoryProvider.createDir("other")
        groovyLoader.addURL(dir.toURI().toURL())
        addPluginId(dir, "plugin", pluginClass)
        manager.apply(pluginClass)

        then:
        applied.size() == 1

        when:
        manager.apply(Imperative)

        then:
        thrown InvalidPluginException // duplicate

        when:
        manager.apply("plugin")

        then:
        noExceptionThrown()

        when:
        manager.pluginContainer.apply("plugin")

        then:
        thrown InvalidPluginException // duplicate
    }

    void addPluginId(String id, Class<?> pluginClass) {
        addPluginId(testDirectoryProvider.testDirectory, id, pluginClass)
    }

    static void addPluginId(TestFile dir, String id, Class<?> pluginClass) {
        dir.file("META-INF/gradle-plugins/${id}.properties") << "implementation-class=$pluginClass.name"
    }
}
