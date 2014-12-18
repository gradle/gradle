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

import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultPluginManagerTest extends Specification {

    def classLoader = new GroovyClassLoader(getClass().classLoader)
    def registry = new DefaultPluginRegistry(new PluginInspector(new ModelRuleSourceDetector()), classLoader)
    def applicator = Mock(PluginApplicator)
    def manager = new DefaultPluginManager(registry, new DirectInstantiator(), applicator)

    Class<?> rulesClass
    Class<?> hybridClass
    Class<?> imperativeClass

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider()

    def setup() {
        rulesClass = classLoader.parseClass("""
            @org.gradle.model.RuleSource
            class Rules {

            }
        """)

        hybridClass = classLoader.parseClass("""
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class Hybrid implements Plugin<Project> {
                @Override
                void apply(Project target) {

                }

                @org.gradle.model.RuleSource
                static class Rules {}
            }
        """)

        imperativeClass = classLoader.parseClass("""
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class Imperative implements Plugin<Project> {
                @Override
                void apply(Project target) {

                }
            }
        """)

        classLoader.addURL(testDirectoryProvider.testDirectory.toURI().toURL())
    }

    def "empty manager ops"() {
        expect:
        manager.pluginContainer.isEmpty()
        !manager.hasPlugin("foo")
    }


    def "can apply rules plugin with no id"() {
        when:
        manager.apply(rulesClass)

        then:
        1 * applicator.applyRules(null, rulesClass)

        and:
        manager.pluginContainer.isEmpty()
    }

    def "can apply rules plugin by class with id"() {
        given:
        addPluginId("foo", rulesClass)

        when:
        manager.apply(rulesClass)

        then:
        1 * applicator.applyRules(null, rulesClass)

        and:
        manager.pluginContainer.isEmpty()
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply rules plugin by id"() {
        given:
        addPluginId("foo", rulesClass)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyRules("foo", rulesClass)

        and:
        manager.pluginContainer.isEmpty()
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply hybrid plugin with no id"() {
        when:
        manager.apply(hybridClass)

        then:
        1 * applicator.applyImperativeRulesHybrid(null, { hybridClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply hybrid plugin by class with id"() {
        given:
        addPluginId("foo", hybridClass)

        when:
        manager.apply(hybridClass)

        then:
        1 * applicator.applyImperativeRulesHybrid(null, { hybridClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply hybrid plugin by id"() {
        given:
        addPluginId("foo", hybridClass)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyImperativeRulesHybrid("foo", { hybridClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply imperative plugin with no id"() {
        when:
        manager.apply(imperativeClass)

        then:
        1 * applicator.applyImperative(null, { imperativeClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply imperative plugin by class with id"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.apply(imperativeClass)

        then:
        1 * applicator.applyImperative(null, { imperativeClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "can apply imperative plugin by id"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.apply("foo")

        then:
        1 * applicator.applyImperative("foo", { imperativeClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
        def called = false
        manager.withPlugin("foo") {
            assert it.id == "foo"
            assert it.namespace == null
            assert it.name == "foo"
            called = true
        }
        called
    }

    def "with plugin fires for each plugin known by that id"() {
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
        addPluginId("plugin", imperativeClass)
        manager.withPlugin("plugin") { applied << it }

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
        manager.apply(imperativeClass)

        then:
        applied.size() == 2
    }

    void addPluginId(String id, Class<?> pluginClass) {
        addPluginId(testDirectoryProvider.testDirectory, id, pluginClass)
    }

    static void addPluginId(TestFile dir, String id, Class<?> pluginClass) {
        dir.file("META-INF/gradle-plugins/${id}.properties") << "implementation-class=$pluginClass.name"
    }
}
