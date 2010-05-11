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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.internal.project.TestPlugin1;
import org.gradle.api.internal.project.TestPlugin2;
import org.gradle.api.plugins.PluginInstantiationException;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.GUtil;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

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
        classLoader = createClassLoader(pluginId, TestPlugin1.class.getName(), "parent");
        pluginRegistry = new DefaultPluginRegistry(classLoader);
    }

    private ClassLoader createClassLoader(final String id, String implClass, String name) throws IOException {
        TestFile classPath = testDir.createDir(name);
        Properties props = new Properties();
        props.setProperty("implementation-class", implClass);
        final TestFile propertiesFile = classPath.file(id + ".properties");
        propertiesFile.getParentFile().mkdirs();
        GUtil.saveProperties(props, propertiesFile);
        final ClassLoader classLoader = context.mock(ClassLoader.class, name);
        context.checking(new Expectations() {{
            allowing(classLoader).getResource("META-INF/gradle-plugins/" + id + ".properties");
            will(returnValue(propertiesFile.toURI().toURL()));
        }});
        return classLoader;
    }

    @Test
    public void canLoadPluginByType() {
        assertThat(pluginRegistry.loadPlugin(TestPlugin2.class), instanceOf(TestPlugin2.class));
    }

    @Test
    public void canLookupPluginTypeById() throws ClassNotFoundException {
        expectClassLoaded(classLoader, TestPlugin1.class);

        assertThat(pluginRegistry.getTypeForId(pluginId), equalTo((Class) TestPlugin1.class));
    }

    @Test
    public void failsForUnknownId() {
        expectResourceNotFound(classLoader, "unknownId");

        try {
            pluginRegistry.getTypeForId("unknownId");
            fail();
        } catch (UnknownPluginException e) {
            assertThat(e.getMessage(), equalTo("Plugin with id 'unknownId' not found."));
        }
    }

    @Test
    public void failsWhenClassDoesNotImplementPlugin() {
        try {
            pluginRegistry.loadPlugin((Class)String.class);
            fail();
        } catch (InvalidUserDataException e) {
            assertThat(e.getMessage(), equalTo("Cannot create plugin of type 'String' as it does not implement the Plugin interface."));
        }
    }

    @Test
    public void failsWhenNoImplementationClassSpecifiedInPropertiesFile() throws MalformedURLException {
        Properties properties = new Properties();
        final TestFile propertiesFile = testDir.file("prop");
        GUtil.saveProperties(properties, propertiesFile);
        final URL url = propertiesFile.toURI().toURL();

        context.checking(new Expectations() {{
            allowing(classLoader).getResource("META-INF/gradle-plugins/noImpl.properties");
            will(returnValue(url));
        }});

        try {
            pluginRegistry.getTypeForId("noImpl");
            fail();
        } catch (PluginInstantiationException e) {
            assertThat(e.getMessage(), equalTo("No implementation class specified for plugin 'noImpl' in " + url + "."));
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
        ClassLoader childClassLoader = createClassLoader("other", TestPlugin1.class.getName(), "child");

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.loadPlugin(TestPlugin1.class), instanceOf(TestPlugin1.class));
    }

    @Test
    public void childDelegatesToParentRegistryToLookupPluginType() throws Exception {
        expectClassLoaded(classLoader, TestPlugin1.class);

        ClassLoader childClassLoader = createClassLoader("other", TestPlugin1.class.getName(), "child");

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.getTypeForId(pluginId), equalTo((Class) pluginRegistry.getTypeForId(pluginId)));
    }

    @Test
    public void childClasspathCanContainAdditionalMappingsForPlugins() throws Exception {
        expectResourceNotFound(classLoader, "other");

        ClassLoader childClassLoader = createClassLoader("other", TestPlugin1.class.getName(), "child");
        expectClassLoaded(childClassLoader, TestPlugin1.class);

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.getTypeForId("other"), equalTo((Class) TestPlugin1.class));
    }

    @Test
    public void parentIdMappingHasPrecedenceOverChildIdMapping() throws Exception {
        expectClassLoaded(classLoader, TestPlugin1.class);
        ClassLoader childClassLoader = createClassLoader(pluginId, "no-such-class", "child");

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.getTypeForId(pluginId), equalTo((Class) pluginRegistry.getTypeForId(pluginId)));
    }

    @Test
    public void childClasspathCanContainAdditionalPlugins() throws Exception {
        expectClassesNotFound(classLoader);
        expectResourceNotFound(classLoader, "other");

        ClassLoader childClassLoader = createClassLoader("other", TestPlugin2.class.getName(), "child");
        expectClassLoaded(childClassLoader, TestPlugin2.class);

        PluginRegistry child = pluginRegistry.createChild(childClassLoader);
        assertThat(child.getTypeForId("other"), equalTo((Class) TestPlugin2.class));
    }

    private void expectResourceNotFound(final ClassLoader classLoader, final String id) {
        context.checking(new Expectations(){{
            allowing(classLoader).getResource("META-INF/gradle-plugins/" + id + ".properties");
            will(returnValue(null));
        }});
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
        public void apply(String target) {
        }
    }
}
