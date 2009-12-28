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
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.integtests.TestFile;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.WrapUtil;
import org.junit.Assert;
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

public class DefaultPluginRegistryTest extends AbstractPluginContainerTest {
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
        classLoader = new URLClassLoader(new URL[]{testDir.getDir().toURI().toURL()});
    }

    protected DefaultPluginRegistry getPluginContainer() {
        if (pluginRegistry == null) {
            pluginRegistry = new DefaultPluginRegistry(classLoader);
        }
        return pluginRegistry;
    }

    protected Plugin getPluginWithId() {
        return getPluginContainer().loadPlugin(pluginId);
    }

    protected Plugin getPluginWithoutId() {
        return getPluginContainer().loadPlugin(TestPlugin2.class);
    }

    protected Plugin addWithType(Class<? extends Plugin> type) {
        return getPluginContainer().loadPlugin(type);
    }

    protected Plugin addWithId(String pluginId) {
        return getPluginContainer().loadPlugin(pluginId);
    }

    @Test
    public void testLoadPlugin() throws IOException {
        DefaultPluginRegistry pluginRegistry = getPluginContainer();
        TestPlugin1 testPlugin1 = (TestPlugin1) pluginRegistry.loadPlugin(pluginId);
        Assert.assertThat((TestPlugin1) pluginRegistry.loadPlugin(pluginId), sameInstance(testPlugin1));
        Assert.assertThat(pluginRegistry.loadPlugin(TestPlugin1.class), sameInstance(testPlugin1));
        assertTrue(pluginRegistry.loadPlugin(TestPlugin2.class) instanceof TestPlugin2);
    }

    @Test(expected = UnknownPluginException.class)
    public void testWithNonExistingId() {
        getPluginContainer().loadPlugin("unknownId");
    }
}
