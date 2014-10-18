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
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.model.internal.inspect.ModelRuleSourceDetector
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultPluginRegistryTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    final classLoader = Mock(ClassLoader)
    def pluginInspector = new PluginInspector(new ModelRuleSourceDetector())
    private DefaultPluginRegistry pluginRegistry = new DefaultPluginRegistry(pluginInspector, classLoader)

    public void canLookupPluginTypeById() {
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        pluginRegistry.lookup("somePlugin").asClass() == TestPlugin1
    }

    public void canLookupTypesById() {
        def ruleUrl = writePluginProperties("someRuleSource", TestRuleSource)
        def pluginUrl = writePluginProperties("somePlugin", TestPlugin1)

        when:
        classLoader.getResource("META-INF/gradle-plugins/someRuleSource.properties") >> ruleUrl
        classLoader.loadClass(TestRuleSource.name) >> TestRuleSource

        then:
        pluginRegistry.lookup("someRuleSource").asClass() == TestRuleSource

        when:
        classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> pluginUrl
        classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        then:
        pluginRegistry.lookup("somePlugin").asClass() == TestPlugin1
    }

    public void failsForRuleSourceWhenLookingForAPlugin() {

        def url = writePluginProperties("someRuleSource", TestRuleSource)

        given:
        classLoader.getResource("META-INF/gradle-plugins/someRuleSource.properties") >> url
        classLoader.loadClass(TestRuleSource.name) >> TestRuleSource

        expect:
        pluginRegistry.inspect((Class) TestRuleSource.class).type == PotentialPlugin.Type.PURE_RULE_SOURCE_CLASS
    }

    public void failsForUnknownId() {
        when:
        pluginRegistry.lookup("unknownId")

        then:
        UnknownPluginException e = thrown()
        e.message == "Plugin with id 'unknownId' not found."

        when:
        pluginRegistry.lookup("unknownId")

        then:
        e = thrown()
        e.message == "Plugin with id 'unknownId' not found."
    }

    public void returnsUnknownType() {
        expect:
        pluginRegistry.inspect((Class) String.class).type == PotentialPlugin.Type.UNKNOWN
    }

    public void failsWhenNoImplementationClassSpecifiedInPropertiesFile() {
        def properties = new Properties()
        def propertiesFile = testDir.file("prop")
        GUtil.saveProperties(properties, propertiesFile)
        def url = propertiesFile.toURI().toURL()

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/noImpl.properties") >> url

        when:
        pluginRegistry.lookup("noImpl")

        then:
        PluginInstantiationException e = thrown()
        e.message == "No implementation class specified for plugin 'noImpl' in $url."

        when:
        pluginRegistry.lookup("noImpl")

        then:
        e = thrown()
        e.message == "No implementation class specified for plugin 'noImpl' in $url."
    }

    public void failsWhenImplementationClassSpecifiedInPropertiesFileDoesNotImplementPlugin() {
        def url = writePluginProperties("brokenImpl", String)

        given:
        classLoader.getResource("META-INF/gradle-plugins/brokenImpl.properties") >> url
        classLoader.loadClass(String.name) >> String

        expect:
        pluginRegistry.lookup("brokenImpl").type == PotentialPlugin.Type.UNKNOWN
    }

    public void wrapsFailureToLoadImplementationClass() {
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> { throw new ClassNotFoundException() }

        when:
        pluginRegistry.lookup("somePlugin")

        then:
        InvalidPluginException e = thrown()
        e.message == "Could not find implementation class '$TestPlugin1.name' for plugin 'somePlugin' specified in $url."
        e.cause instanceof ClassNotFoundException
    }

    public void childDelegatesToParentRegistryToLookupPluginType() throws Exception {
        def lookupScope = Mock(ClassLoaderScope)
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(lookupScope)
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def type = child.lookup("somePlugin").asClass()

        then:
        type == TestPlugin1

        and:
        0 * lookupScope._
    }

    public void childClasspathCanContainAdditionalMappingsForPlugins() throws Exception {
        def childClassLoader = Mock(ClassLoader)
        def lookupScope = Mock(ClassLoaderScope)
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(lookupScope)
        _ * lookupScope.localClassLoader >> childClassLoader
        _ * childClassLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * childClassLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def type = child.lookup("somePlugin").asClass()

        then:
        type == TestPlugin1
    }

    private URL writePluginProperties(String pluginId, Class implClass) {
        def props = new Properties()
        props.setProperty("implementation-class", implClass.name)
        def propertiesFile = testDir.file("${pluginId}.properties")
        propertiesFile.getParentFile().mkdirs()
        GUtil.saveProperties(props, propertiesFile)
        return propertiesFile.toURI().toURL()
    }

    private class BrokenPlugin implements Plugin<String> {
        public void apply(String target) {
        }
    }

}
