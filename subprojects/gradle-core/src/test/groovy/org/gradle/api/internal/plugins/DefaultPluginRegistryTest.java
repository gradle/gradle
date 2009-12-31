/*
 * Copyright 2009 the original author or authors.
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
import org.gradle.integtests.TestFile;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Properties;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */

public class DefaultPluginRegistryTest {
    private String pluginId = "test";
    private DefaultPluginRegistry pluginRegistry;
    @Rule
    public TemporaryFolder testDir = new TemporaryFolder();
    private URLClassLoader classLoader;

    @Before
    public void setup() throws Exception {
        Properties properties = new Properties();
        properties.putAll(WrapUtil.toMap(pluginId, TestPlugin1.class.getName()));
        TestFile propertiesFile = testDir.file("META-INF/gradle-plugins.properties");
        try {
            properties.store(new FileOutputStream(propertiesFile), "");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        classLoader = new URLClassLoader(new URL[]{testDir.getDir().toURI().toURL()}, ClassLoader.getSystemClassLoader().getParent());
        pluginRegistry = new DefaultPluginRegistry(classLoader);
    }

    @Test
    public void canLoadPluginByType() {
        DefaultPluginRegistry pluginRegistry = this.pluginRegistry;
        assertThat(pluginRegistry.loadPlugin(TestPlugin2.class), instanceOf(TestPlugin2.class));
    }

    @Test
    public void canLoadPluginById() {
        DefaultPluginRegistry pluginRegistry = this.pluginRegistry;
        assertThat(pluginRegistry.loadPlugin(pluginId), instanceOf(TestPlugin1.class));
    }

    @Test
    public void cachesPluginsByType() {
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

    private class BrokenPlugin implements Plugin<String> {
        public void use(String target) {
        }
    }
}
