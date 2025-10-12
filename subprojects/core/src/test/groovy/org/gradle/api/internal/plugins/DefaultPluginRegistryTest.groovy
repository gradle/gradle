/*
 * Copyright 2012 the original author or authors.
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
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.project.TestPlugin1
import org.gradle.api.internal.project.TestRuleSource
import org.gradle.api.plugins.InvalidPluginException
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.plugin.use.internal.DefaultPluginId
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.internal.GUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultPluginRegistryTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider(getClass())
    def classLoader = Mock(ClassLoader)
    def classLoaderScope = Stub(ClassLoaderScope) {
        getLocalClassLoader() >> classLoader
    }
    def pluginInspector = new PluginInspector(new ModelRuleSourceDetector())
    private DefaultPluginRegistry pluginRegistry = new DefaultPluginRegistry(pluginInspector, classLoaderScope)

    def "can locate imperative plugin implementation given an id"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.lookup(DefaultPluginId.of("somePlugin"))
        plugin.pluginId == DefaultPluginId.of("somePlugin")
        plugin.type == PotentialPlugin.Type.IMPERATIVE_CLASS
        plugin.displayName.displayName == "plugin 'somePlugin'"
        plugin.asClass() == TestPlugin1
    }

    def "can locate rule source plugin implementation given an id"() {
        def ruleUrl = writePluginProperties(TestRuleSource)

        given:
        classLoader.getResource("META-INF/gradle-plugins/someRuleSource.properties") >> ruleUrl
        classLoader.loadClass(TestRuleSource.name) >> TestRuleSource

        expect:
        def plugin = pluginRegistry.lookup(DefaultPluginId.of("someRuleSource"))
        plugin.pluginId == DefaultPluginId.of("someRuleSource")
        plugin.type == PotentialPlugin.Type.PURE_RULE_SOURCE_CLASS
        plugin.displayName.displayName == "plugin 'someRuleSource'"
        plugin.asClass() == TestRuleSource
    }

    def "locate returns null for unknown id"() {
        expect:
        pluginRegistry.lookup(DefaultPluginId.of("unknownId")) == null
    }

    def "can locate plugin implementation in org.gradle namespace using unqualified id"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/org.gradle.somePlugin.properties") >> url
        classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> { throw new RuntimeException() }
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def unqualified = pluginRegistry.lookup(DefaultPluginId.of("somePlugin"))
        unqualified.pluginId == DefaultPluginId.of("org.gradle.somePlugin")
        unqualified.displayName.displayName == "plugin 'org.gradle.somePlugin'"
        unqualified.asClass() == TestPlugin1

        def qualified = pluginRegistry.lookup(DefaultPluginId.of("org.gradle.somePlugin"))
        qualified.pluginId == DefaultPluginId.of("org.gradle.somePlugin")
        unqualified.displayName.displayName == "plugin 'org.gradle.somePlugin'"
        qualified.asClass() == TestPlugin1
    }

    def "does not search in org.gradle namespace when id is qualified with some other namespace"() {
        given:
        classLoader.getResource("META-INF/gradle-plugins/org.gradle.thing.somePlugin.properties") >> { throw new RuntimeException() }

        expect:
        pluginRegistry.lookup(DefaultPluginId.of("thing.somePlugin")) == null
    }

    def "plugin implementation with id in org.gradle namespace is also known by unqualified id"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/org.gradle.somePlugin.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def qualified = pluginRegistry.lookup(DefaultPluginId.of("org.gradle.somePlugin"))
        qualified.isAlsoKnownAs(DefaultPluginId.of("org.gradle.somePlugin"))
        qualified.isAlsoKnownAs(DefaultPluginId.of("somePlugin"))

        def unqualified = pluginRegistry.lookup(DefaultPluginId.of("somePlugin"))
        unqualified.isAlsoKnownAs(DefaultPluginId.of("org.gradle.somePlugin"))
        unqualified.isAlsoKnownAs(DefaultPluginId.of("somePlugin"))
    }

    def "plugin implementation is also known by all id mappings that reference that implementation"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/plugin-1.properties") >> url
        classLoader.getResource("META-INF/gradle-plugins/plugin-2.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin1 = pluginRegistry.lookup(DefaultPluginId.of("plugin-1"))
        def plugin2 = pluginRegistry.lookup(DefaultPluginId.of("plugin-2"))

        plugin1.isAlsoKnownAs(DefaultPluginId.of("plugin-1"))
        plugin1.isAlsoKnownAs(DefaultPluginId.of("plugin-2"))
        plugin2.isAlsoKnownAs(DefaultPluginId.of("plugin-1"))
        plugin2.isAlsoKnownAs(DefaultPluginId.of("plugin-2"))
    }

    def "plugin implementation is not known by unknown id"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/thing.somePlugin.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.lookup(DefaultPluginId.of("thing.somePlugin"))
        !plugin.isAlsoKnownAs(DefaultPluginId.of("somePlugin"))
        !plugin.isAlsoKnownAs(DefaultPluginId.of("thing.other"))
        !plugin.isAlsoKnownAs(DefaultPluginId.of("org.gradle.thing.somePlugin"))
    }

    def "plugin implementation is not known by id that maps to another implementation"() {
        def url1 = writePluginProperties(TestPlugin1)
        def url2 = writePluginProperties(BrokenPlugin)

        given:
        classLoader.getResource("META-INF/gradle-plugins/plugin-1.properties") >> url1
        classLoader.getResource("META-INF/gradle-plugins/plugin-2.properties") >> url2
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1
        classLoader.loadClass(BrokenPlugin.name) >> BrokenPlugin

        expect:
        def plugin1 = pluginRegistry.lookup(DefaultPluginId.of("plugin-1"))
        def plugin2 = pluginRegistry.lookup(DefaultPluginId.of("plugin-2"))

        !plugin1.isAlsoKnownAs(DefaultPluginId.of("plugin-2"))
        !plugin2.isAlsoKnownAs(DefaultPluginId.of("plugin-1"))
    }

    def "inspects imperative plugin implementation"() {
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.inspect(TestPlugin1.class)
        plugin.type == PotentialPlugin.Type.IMPERATIVE_CLASS
        plugin.pluginId == null
        plugin.displayName.displayName == "plugin class '${TestPlugin1.name}'"
    }

    def "inspects imperative plugin implementation that has no id mapping"() {
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.inspect(TestPlugin1.class)
        !plugin.isAlsoKnownAs(DefaultPluginId.of("org.gradle.some-plugin"))
    }

    def "inspects class that has multiple id mappings"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/plugin-1.properties") >> url
        classLoader.getResource("META-INF/gradle-plugins/plugin-2.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.lookup(DefaultPluginId.of("plugin-1"), classLoader)
        plugin.isAlsoKnownAs(DefaultPluginId.of("plugin-1"))
        plugin.isAlsoKnownAs(DefaultPluginId.of("plugin-2"))
    }

    def "inspects class that has id mapping in org.gradle namespace"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        classLoader.getResource("META-INF/gradle-plugins/org.gradle.somePlugin.properties") >> url
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        def plugin = pluginRegistry.lookup(DefaultPluginId.of("org.gradle.somePlugin"), classLoader)
        plugin.isAlsoKnownAs(DefaultPluginId.of("somePlugin"))
        plugin.isAlsoKnownAs(DefaultPluginId.of("org.gradle.somePlugin"))
    }

    def "inspects class that is not a plugin implementation"() {
        classLoader.loadClass(String.name) >> String

        expect:
        pluginRegistry.inspect(String.class).type == PotentialPlugin.Type.UNKNOWN
    }

    def "fails when no implementation class specified in properties file"() {
        def properties = new Properties()
        def propertiesFile = testDir.file("prop")
        GUtil.saveProperties(properties, propertiesFile)
        def url = propertiesFile.toURI().toURL()

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/noImpl.properties") >> url

        when:
        pluginRegistry.lookup(DefaultPluginId.of("noImpl"))

        then:
        InvalidPluginException e = thrown()
        e.message == "No implementation class specified for plugin 'noImpl' in $url."

        when:
        pluginRegistry.lookup(DefaultPluginId.of("noImpl"))

        then:
        e = thrown()
        e.message == "No implementation class specified for plugin 'noImpl' in $url."
    }

    def "can locate a plugin implementation that is neither imperative or rule source"() {
        def url = writePluginProperties(String)

        given:
        classLoader.getResource("META-INF/gradle-plugins/brokenImpl.properties") >> url
        classLoader.loadClass(String.name) >> String

        expect:
        pluginRegistry.lookup(DefaultPluginId.of("brokenImpl")).type == PotentialPlugin.Type.UNKNOWN
    }

    def "wraps failure to load implementation class"() {
        def url = writePluginProperties(TestPlugin1)

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> { throw new ClassNotFoundException() }

        when:
        pluginRegistry.lookup(DefaultPluginId.of("somePlugin"))

        then:
        InvalidPluginException e = thrown()
        e.message == "Could not find implementation class '$TestPlugin1.name' for plugin 'somePlugin' specified in $url."
        e.cause instanceof ClassNotFoundException
    }

    def "child delegates to parent registry to locate implementation from an id"() throws Exception {
        def lookupScope = Mock(ClassLoaderScope)
        def url = writePluginProperties(TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(lookupScope)
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def plugin = child.lookup(DefaultPluginId.of("somePlugin"))

        then:
        plugin.asClass() == TestPlugin1

        and:
        0 * lookupScope._
    }

    def "child classpath can container additional mappings"() throws Exception {
        def childClassLoader = Mock(ClassLoader)
        def lookupScope = Mock(ClassLoaderScope)
        def url = writePluginProperties(TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(lookupScope)
        _ * lookupScope.localClassLoader >> childClassLoader
        _ * childClassLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * childClassLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def type = child.lookup(DefaultPluginId.of("somePlugin")).asClass()

        then:
        type == TestPlugin1
    }

    private URL writePluginProperties(Class implClass) {
        def props = new Properties()
        props.setProperty("implementation-class", implClass.name)
        def propertiesFile = testDir.file("${implClass}.properties")
        propertiesFile.getParentFile().mkdirs()
        GUtil.saveProperties(props, propertiesFile)
        return propertiesFile.toURI().toURL()
    }

    private class BrokenPlugin implements Plugin<String> {
        public void apply(String target) {
        }
    }

}
