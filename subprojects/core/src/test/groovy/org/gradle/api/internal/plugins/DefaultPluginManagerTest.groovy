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

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.internal.CollectionCallbackActionDecorator
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.internal.code.DefaultUserCodeApplicationContext
import org.gradle.internal.operations.TestBuildOperationRunner
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultPluginManagerTest extends Specification {

    def classLoader = new GroovyClassLoader(getClass().classLoader)
    def classLoaderScope = Stub(ClassLoaderScope) {
        getLocalClassLoader() >> classLoader
    }
    def registry = new DefaultPluginRegistry(new PluginInspector(new ModelRuleSourceDetector()), classLoaderScope)
    def target = Mock(PluginTarget)
    def manager = new DefaultPluginManager(registry, TestUtil.instantiatorFactory().inject(), target, new TestBuildOperationRunner(), new DefaultUserCodeApplicationContext(), CollectionCallbackActionDecorator.NOOP, TestUtil.domainObjectCollectionFactory())

    Class<?> rulesClass
    Class<? extends Plugin> hybridClass
    Class<? extends Plugin> imperativeClass

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def setup() {
        rulesClass = classLoader.parseClass("""
            class Rules extends org.gradle.model.RuleSource {

            }
        """)

        hybridClass = classLoader.parseClass("""
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class Hybrid implements Plugin<Project> {
                @Override
                void apply(Project target) {

                }

                static class Rules extends org.gradle.model.RuleSource {}
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
        manager.pluginContainer.empty
        !manager.hasPlugin("foo")
        manager.findPlugin("foo") == null
    }

    def "can apply rules plugin with no id"() {
        when:
        manager.apply(rulesClass)

        then:
        1 * target.applyRules(null, rulesClass)

        and:
        manager.pluginContainer.isEmpty()
    }

    def "can apply rules plugin by class with id"() {
        given:
        addPluginId("foo", rulesClass)

        when:
        manager.apply(rulesClass)

        then:
        1 * target.applyRules(null, rulesClass)

        and:
        manager.hasPlugin("foo")
    }

    def "action is notified when rules plugin with id is applied by class"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", rulesClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply(rulesClass)

        then:
        1 * action.execute(_) >> { AppliedPlugin p ->
            assert p.id == "foo"
            assert p.namespace == null
            assert p.name == "foo"
        }
        0 * action._
    }

    def "rules plugin is applied at most once"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", rulesClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply(rulesClass)
        manager.apply("foo")
        manager.apply(rulesClass)

        then:
        1 * target.applyRules(null, rulesClass)
        1 * target.getConfigurationTargetIdentifier()
        1 * action.execute(_)
        0 * target._
        0 * action._

        and:
        manager.hasPlugin("foo")
    }

    def "can apply rules plugin by id"() {
        given:
        addPluginId("foo", rulesClass)

        when:
        manager.apply("foo")

        then:
        1 * target.applyRules("foo", rulesClass)

        and:
        manager.pluginContainer.isEmpty()
        manager.hasPlugin("foo")
    }

    def "action is notified when rules plugin with id is applied by id"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", rulesClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply("foo")

        then:
        1 * action.execute(_) >> { AppliedPlugin p ->
            assert p.id == "foo"
            assert p.namespace == null
            assert p.name == "foo"
        }
        0 * action._
    }

    def "rules plugin with id does not appear in plugin container"() {
        given:
        addPluginId("foo", rulesClass)

        when:
        manager.apply("foo")

        then:
        manager.pluginContainer.isEmpty()
        manager.pluginContainer.findPlugin(rulesClass) == null
        manager.pluginContainer.findPlugin("foo") == null
        !manager.pluginContainer.hasPlugin(rulesClass)
        !manager.pluginContainer.hasPlugin("foo")
    }

    def "can apply hybrid plugin with no id"() {
        when:
        manager.apply(hybridClass)

        then:
        1 * target.applyImperativeRulesHybrid(null, { hybridClass.isInstance(it) }, hybridClass)

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply hybrid plugin by class with id"() {
        given:
        addPluginId("foo", hybridClass)

        when:
        manager.apply(hybridClass)

        then:
        1 * target.applyImperativeRulesHybrid(null, { hybridClass.isInstance(it) }, hybridClass)

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
        1 * target.applyImperativeRulesHybrid("foo", { hybridClass.isInstance(it) }, hybridClass)

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

    def "hybrid plugin is applied at most once"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", hybridClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply(hybridClass)
        manager.apply("foo")
        manager.apply(hybridClass)

        then:
        1 * target.getConfigurationTargetIdentifier()
        1 * target.applyImperativeRulesHybrid(null, { hybridClass.isInstance(it) }, hybridClass)
        0 * target._
        1 * action.execute(_)
        0 * action._

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
    }

    def "hybrid plugin with id appears in plugins container"() {
        given:
        addPluginId("foo", hybridClass)

        when:
        manager.apply("foo")

        then:
        manager.pluginContainer.size() == 1
        manager.pluginContainer.findPlugin(hybridClass) != null
        manager.pluginContainer.findPlugin("foo") != null
        manager.pluginContainer.hasPlugin(hybridClass)
        manager.pluginContainer.hasPlugin("foo")
    }

    def "can apply imperative plugin with no id"() {
        when:
        manager.apply(imperativeClass)

        then:
        1 * target.applyImperative(null, { imperativeClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
    }

    def "can apply imperative plugin by class with id"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.apply(imperativeClass)

        then:
        1 * target.applyImperative(null, { imperativeClass.isInstance(it) })

        and:
        manager.pluginContainer.size() == 1
        manager.hasPlugin("foo")
    }

    def "can apply imperative plugin by id"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.apply("foo")

        then:
        1 * target.applyImperative("foo", { imperativeClass.isInstance(it) })

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

    def "imperative plugin applied via plugins container is visible via plugins manager"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.pluginContainer.apply(imperativeClass)

        then:
        1 * target.getConfigurationTargetIdentifier()
        1 * target.applyImperative(null, { imperativeClass.isInstance(it) })
        0 * target._

        and:
        manager.pluginContainer.size() == 1
        manager.pluginContainer.hasPlugin("foo")
        manager.pluginContainer.hasPlugin(imperativeClass)
        manager.hasPlugin("foo")
    }

    def "imperative plugin is applied at most once"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", imperativeClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply(imperativeClass)
        manager.apply("foo")
        manager.apply(imperativeClass)

        then:
        1 * target.getConfigurationTargetIdentifier()
        1 * target.applyImperative(null, { imperativeClass.isInstance(it) })
        0 * target._
        1 * action.execute(_)
        0 * action._
    }

    def "imperative plugin with id appears in plugins container"() {
        given:
        addPluginId("foo", imperativeClass)

        when:
        manager.apply("foo")

        then:
        manager.pluginContainer.size() == 1
        manager.pluginContainer.findPlugin("foo") != null
        manager.pluginContainer.findPlugin(imperativeClass) != null
        manager.pluginContainer.hasPlugin(imperativeClass)
        manager.pluginContainer.hasPlugin("foo")
    }

    def "action is notified when imperative plugin with id is applied"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", imperativeClass)
        manager.withPlugin("foo", action)
        manager.pluginContainer.withId("foo", action)

        when:
        manager.apply(imperativeClass)

        then:
        1 * action.execute(_) >> { AppliedPlugin p ->
            assert p.id == "foo"
        }
        1 * action.execute(_) >> { Plugin p ->
            assert p.class == imperativeClass
        }
        0 * action._
    }

    def "plugin with multiple ids visible with all ids"() {
        given:
        addPluginId("foo", imperativeClass)
        addPluginId("bar", imperativeClass)

        when:
        manager.apply(imperativeClass)

        then:
        manager.hasPlugin("foo")
        manager.hasPlugin("bar")
        manager.pluginContainer.size() == 1
        manager.pluginContainer.hasPlugin("foo")
        manager.pluginContainer.hasPlugin("bar")
        manager.pluginContainer.hasPlugin(imperativeClass)
    }

    def "plugin with org.gradle id is visible with unqualified id"() {
        given:
        addPluginId("org.gradle.foo", imperativeClass)

        when:
        manager.apply(imperativeClass)

        then:
        manager.hasPlugin("foo")
        manager.hasPlugin("org.gradle.foo")

        manager.findPlugin("foo") != null
        manager.findPlugin("org.gradle.foo") != null

        manager.pluginContainer.size() == 1
        manager.pluginContainer.hasPlugin("foo")
        manager.pluginContainer.hasPlugin("org.gradle.foo")
        manager.pluginContainer.findPlugin("foo") != null
        manager.pluginContainer.hasPlugin("org.gradle.foo") != null
    }

    def "action is notified for plugin with multiple ids"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        given:
        addPluginId("foo", imperativeClass)
        addPluginId("bar", imperativeClass)

        when:
        manager.withPlugin("foo", action1)
        manager.withPlugin("bar", action2)
        manager.apply(imperativeClass)
        manager.withPlugin("foo", action1)
        manager.withPlugin("bar", action2)

        then:
        2 * action1.execute(_) >> { AppliedPlugin p ->
            assert p.id == "foo"
        }
        2 * action2.execute(_) >> { AppliedPlugin p ->
            assert p.id == "bar"
        }
        0 * action1._
        0 * action2._
    }

    def "action is notified for plugin with org.gradle id"() {
        def action1 = Mock(Action)
        def action2 = Mock(Action)

        given:
        addPluginId("org.gradle.foo", imperativeClass)

        when:
        manager.withPlugin("foo", action1)
        manager.withPlugin("org.gradle.foo", action2)
        manager.apply(imperativeClass)
        manager.withPlugin("foo", action1)
        manager.withPlugin("org.gradle.foo", action2)

        then:
        2 * action1.execute(_) >> { AppliedPlugin p ->
            assert p.id == "foo"
        }
        2 * action2.execute(_) >> { AppliedPlugin p ->
            assert p.id == "org.gradle.foo"
        }
        0 * action1._
        0 * action2._
    }

    def "plugin with multiple ids is applied at most once"() {
        def action = Mock(Action)

        given:
        addPluginId("foo", imperativeClass)
        addPluginId("bar", imperativeClass)
        manager.withPlugin("foo", action)

        when:
        manager.apply("bar")
        manager.apply("foo")
        manager.apply(imperativeClass)

        then:
        1 * target.getConfigurationTargetIdentifier()
        1 * target.applyImperative("bar", { imperativeClass.isInstance(it) })
        0 * target._
        1 * action.execute(_)
        0 * action._

        and:
        manager.pluginContainer.size() == 1
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
