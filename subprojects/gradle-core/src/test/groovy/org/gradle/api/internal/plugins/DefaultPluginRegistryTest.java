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
package org.gradle.api.internal.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.internal.project.TestPlugin1;
import org.gradle.api.internal.project.TestPlugin2;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;
import org.gradle.util.TestFile;
import org.gradle.util.TemporaryFolder;
import org.hamcrest.Matchers;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
@RunWith(JMock.class)
public class DefaultPluginRegistryTest {
    private String pluginId = "test";
    private DefaultPluginRegistry pluginRegistry;
    private JUnit4Mockery context = new JUnit4Mockery() {{
        setImposteriser(ClassImposteriser.INSTANCE);
    }};
    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();
    private ClassLoader classLoader;

    @Before
    public void setup() throws Exception {
        classLoader = createClassLoader(toMap(pluginId, TestPlugin1.class.getName()), "parent");

        pluginRegistry = new DefaultPluginRegistry(classLoader);
    }

    private ClassLoader createClassLoader(Map<String, String> properties, String name) throws IOException {
        TestFile classPath = testDir.createDir(name);
        Properties props = new Properties();
        props.putAll(properties);
        final TestFile propertiesFile = classPath.file("META-INF/gradle-plugins.properties");
        propertiesFile.getParentFile().mkdirs();
        GUtil.saveProperties(props, propertiesFile);
        final ClassLoader classLoader = context.mock(ClassLoader.class, name);
        context.checking(new Expectations() {{
            allowing(classLoader).getResources("META-INF/gradle-plugins.properties");
            will(returnEnumeration(propertiesFile.toURI().toURL()));
        }});
        return classLoader;
    }

    @Test
    public void canLoadPluginByType() {
        DefaultPluginRegistry pluginRegistry = this.pluginRegistry;
        assertThat(pluginRegistry.loadPlugin(TestPlugin2.class), instanceOf(TestPlugin2.class));
    }

    @Test
    public void canLoadPluginById() throws ClassNotFoundException {
        expectClassLoaded(classLoader, TestPlugin1.class);

        DefaultPluginRegistry pluginRegistry = this.pluginRegistry;
        assertThat(pluginRegistry.loadPlugin(pluginId), instanceOf(TestPlugin1.class));
    }

    @Test
    public void cachesPluginsByType() throws ClassNotFoundException {
        expectClassLoaded(classLoader, TestPlugin1.class);

        DefaultPluginRegistry pluginRegistry = this.pluginRegistry;
        assertThat(pluginRegistry.loadPlugin(pluginId), sameInstance((Plugin) pluginRegistry.loadPlugin(
                TestPlugin1.class)));
        assertThat(pluginRegistry.loadPlugin(TestPlugin2.class), sameInstance(pluginRegistry.loadPlugin(
                TestPlugin2.class)));
    }

    @Test
    public void failsForUnknownId() {
        try {
            pluginRegistry.loadPlugin("unknownId");
            fail();
        } catch (UnknownPluginException e) {
            assertThat(e.getMessage(), equalTo("Plugin with id 'unknownId' not found."));
        }
    }

    @Test
    public void wrapsPluginInstantiationFailure() {
        try {
            pluginRegistry.loadPlugin(BrokenPlugin.class);
            fail();
        } catch (PluginInstantiationException e) {
            assertThat(e.getMessage(), equalTo("Could not create plugin of type 'BrokenPlugin'."));
            assertThat(e.getCause(), Matchers.<Object>nullValue());
        }
    }

    @Test
    public void childDelegatesToParentRegistryToLoadPlugin() throws Exception {
        expectClassLoaded(classLoader, TestPlugin1.class);

        ClassLoader childClassLoader = createClassLoader(toMap("other", TestPlugin1.class.getName()), "child");

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.loadPlugin(pluginId), sameInstance(pluginRegistry.loadPlugin(pluginId)));
    }

    @Test
    public void childClasspathCanContainAdditionalMappingsForPlugins() throws Exception {
        ClassLoader childClassLoader = createClassLoader(toMap("other", TestPlugin1.class.getName()), "child");
        expectClassLoaded(childClassLoader, TestPlugin1.class);

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.loadPlugin("other"), sameInstance((Plugin) pluginRegistry.loadPlugin(TestPlugin1.class)));
    }

    @Test
    public void parentIdMappingHasPrecedenceOverChildIdMapping() throws Exception {
        expectClassLoaded(classLoader, TestPlugin1.class);
        ClassLoader childClassLoader = createClassLoader(toMap(pluginId, "no-such-class"), "child");

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.loadPlugin(pluginId), sameInstance(pluginRegistry.loadPlugin(pluginId)));
    }

    @Test
    public void childClasspathCanContainAdditionalPlugins() throws Exception {
        expectClassesNotFound(classLoader);

        ClassLoader childClassLoader = createClassLoader(toMap("other", TestPlugin2.class.getName()), "child");
        expectClassLoaded(childClassLoader, TestPlugin2.class);

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.loadPlugin("other"), instanceOf(TestPlugin2.class));
    }

    private void expectClassesNotFound(final ClassLoader classLoader) throws ClassNotFoundException {
        context.checking(new Expectations() {{
            allowing(classLoader).loadClass(with(notNullValue(String.class)));
            will(throwException(new ClassNotFoundException()));
        }});
    }

    private void expectClassLoaded(final ClassLoader classLoader, final Class<?> pluginClass) throws ClassNotFoundException {
        context.checking(new Expectations() {{
            atLeast(1).of(classLoader).loadClass(pluginClass.getName());
            will(returnValue(pluginClass));
        }});
    }

    private class BrokenPlugin implements Plugin<String> {
        public void use(String target) {
        }
    }
}
