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

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.internal.project.TestPlugin1
import org.gradle.api.internal.project.TestPlugin2
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.reflect.ObjectInstantiationException
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.GUtil
import org.junit.Rule
import spock.lang.Specification

class DefaultPluginRegistryTest extends Specification {
    @Rule
    final TestNameTestDirectoryProvider testDir = new TestNameTestDirectoryProvider()
    final Instantiator instantiator = Mock()
    final ClassLoader classLoader = Mock()
    private DefaultPluginRegistry pluginRegistry = new DefaultPluginRegistry(classLoader, instantiator)

    public void canLoadPluginByType() {
        def plugin = new TestPlugin2()

        when:
        def result = pluginRegistry.loadPlugin(TestPlugin2.class)

        then:
        result == plugin

        and:
        1* instantiator.newInstance(TestPlugin2.class, new Object[0]) >> plugin
    }

    public void canLookupPluginTypeById() {
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        expect:
        pluginRegistry.getTypeForId("somePlugin") == TestPlugin1
    }

    public void failsForUnknownId() {
        when:
        pluginRegistry.getTypeForId("unknownId")

        then:
        UnknownPluginException e = thrown()
        e.message == "Plugin with id 'unknownId' not found."
    }

    public void failsWhenClassDoesNotImplementPlugin() {
        when:
        pluginRegistry.loadPlugin((Class)String.class)

        then:
        InvalidUserDataException e = thrown()
        e.message == "Cannot create plugin of type 'String' as it does not implement the Plugin interface."
    }

    public void failsWhenNoImplementationClassSpecifiedInPropertiesFile() {
        def properties = new Properties()
        def propertiesFile = testDir.file("prop")
        GUtil.saveProperties(properties, propertiesFile)
        def url = propertiesFile.toURI().toURL()

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/noImpl.properties") >> url

        when:
        pluginRegistry.getTypeForId("noImpl")

        then:
        PluginInstantiationException e = thrown()
        e.message == "No implementation class specified for plugin 'noImpl' in $url."
    }

    public void failsWhenImplementationClassSpecifiedInPropertiesFileDoesNotImplementPlugin() {
        def properties = new Properties()
        def propertiesFile = testDir.file("prop")
        properties.setProperty("implementation-class", "java.lang.String")
        GUtil.saveProperties(properties, propertiesFile)
        def url = propertiesFile.toURI().toURL()

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/brokenImpl.properties") >> url
        _ * classLoader.loadClass("java.lang.String") >> String

        when:
        pluginRegistry.getTypeForId("brokenImpl")

        then:
        PluginInstantiationException e = thrown()
        e.message == "Implementation class 'java.lang.String' specified for plugin 'brokenImpl' does not implement the Plugin interface. Specified in $url."
    }

    public void wrapsPluginInstantiationFailure() {
        def failure = new RuntimeException();

        given:
        _ * instantiator.newInstance(BrokenPlugin, new Object[0]) >> { throw new ObjectInstantiationException(BrokenPlugin.class, failure) }

        when:
        pluginRegistry.loadPlugin(BrokenPlugin.class)

        then:
        PluginInstantiationException e = thrown()
        e.message == "Could not create plugin of type 'BrokenPlugin'."
        e.cause == failure
    }

    public void wrapsFailureToLoadImplementationClass() {
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> { throw new ClassNotFoundException() }

        when:
        pluginRegistry.getTypeForId("somePlugin")

        then:
        PluginInstantiationException e = thrown()
        e.message == "Could not find implementation class '$TestPlugin1.name' for plugin 'somePlugin' specified in $url."
        e.cause instanceof ClassNotFoundException
    }

    public void childUsesItsOwnInstantiatorToCreatePlugin() {
        ClassLoader childClassLoader = Mock()
        Instantiator childInstantiator = Mock()
        def plugin = new TestPlugin1()

        given:
        PluginRegistry child = pluginRegistry.createChild(childClassLoader, childInstantiator)

        when:
        def result = child.loadPlugin(TestPlugin1)

        then:
        result == plugin

        and:
        1 * childInstantiator.newInstance(TestPlugin1, new Object[0]) >> plugin
        0 * instantiator._
    }

    public void childDelegatesToParentRegistryToLookupPluginType() throws Exception {
        ClassLoader childClassLoader = Mock()
        Instantiator childInstantiator = Mock()
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(childClassLoader, childInstantiator)
        _ * classLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * classLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def type = child.getTypeForId("somePlugin")

        then:
        type == TestPlugin1

        and:
        0 * childClassLoader._
    }

    public void childClasspathCanContainAdditionalMappingsForPlugins() throws Exception {
        ClassLoader childClassLoader = Mock()
        Instantiator childInstantiator = Mock()
        def url = writePluginProperties("somePlugin", TestPlugin1)

        given:
        PluginRegistry child = pluginRegistry.createChild(childClassLoader, childInstantiator)
        _ * childClassLoader.getResource("META-INF/gradle-plugins/somePlugin.properties") >> url
        _ * childClassLoader.loadClass(TestPlugin1.name) >> TestPlugin1

        when:
        def type = child.getTypeForId("somePlugin")

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
