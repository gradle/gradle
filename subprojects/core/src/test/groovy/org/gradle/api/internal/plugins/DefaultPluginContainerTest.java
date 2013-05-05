/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.internal.project.TestPlugin1;
import org.gradle.api.internal.project.TestPlugin2;
import org.gradle.api.plugins.UnknownPluginException;
import org.gradle.util.HelperUtil;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

/**
 * @author Hans Dockter
 */
public class DefaultPluginContainerTest {
    protected String pluginId = "somePluginId";
    protected JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultProject project = HelperUtil.createRootProject();

    private PluginRegistry pluginRegistryStub = context.mock(PluginRegistry.class);
    private DefaultPluginContainer container = new DefaultPluginContainer(pluginRegistryStub, project);

    private TestPlugin1 pluginWithIdMock = new TestPlugin1();
    private TestPlugin2 pluginWithoutIdMock = new TestPlugin2();

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(pluginRegistryStub).getTypeForId(pluginId); will(returnValue(TestPlugin1.class));
            allowing(pluginRegistryStub).loadPlugin(TestPlugin1.class); will(returnValue(pluginWithIdMock));
            allowing(pluginRegistryStub).loadPlugin(TestPlugin2.class); will(returnValue(pluginWithoutIdMock));
        }});
    }

    @Test
    public void usePluginById() {
        Plugin addedPlugin = container.apply(pluginId);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(container.apply(pluginId), sameInstance(addedPlugin));

        assertThat(container.findPlugin(addedPlugin.getClass()), sameInstance(addedPlugin));
        assertThat(container.findPlugin(pluginId), sameInstance(addedPlugin));
    }

    @Test
    public void usePluginWithIdByType() {
        Class<? extends Plugin> type = pluginWithIdMock.getClass();

        Plugin addedPlugin = container.apply(type);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(container.apply(type), sameInstance(addedPlugin));
        assertThat(container.apply(pluginId), sameInstance(addedPlugin));

        assertThat(container.findPlugin(type), sameInstance(addedPlugin));
        assertThat(container.findPlugin(pluginId), sameInstance(addedPlugin));
    }

    @Test
    public void usePluginWithoutId() {
        Class<? extends Plugin> type = pluginWithoutIdMock.getClass();
        Plugin addedPlugin = container.apply(type);
        assertThat(pluginWithoutIdMock, sameInstance(addedPlugin));
        assertThat(container.apply(type), sameInstance(addedPlugin));

        assertThat(container.findPlugin(type), sameInstance(addedPlugin));
    }

    @Test
    public void hasAndFindForPluginWithId() {
        container.apply(pluginId);
        assertThat(container.hasPlugin(pluginId), equalTo(true));
        assertThat(container.hasPlugin(pluginWithIdMock.getClass()), equalTo(true));
        assertThat(container.findPlugin(pluginId), sameInstance((Plugin) pluginWithIdMock));
        assertThat(container.findPlugin(pluginWithIdMock.getClass()), sameInstance((Plugin) pluginWithIdMock));
    }

    @Test
    public void hasAndFindForUnknownPluginId() {
        context.checking(new Expectations() {{
            allowing(pluginRegistryStub).getTypeForId("unknown"); will(throwException(new UnknownPluginException("unknown")));
        }});

        assertThat(container.hasPlugin("unknown"), equalTo(false));
        assertThat(container.findPlugin("unknown"), nullValue());
    }

    @Test
    public void hasAndFindForPluginWithoutId() {
        Plugin plugin = pluginWithoutIdMock;
        Class<? extends Plugin> pluginType = plugin.getClass();
        container.apply(pluginType);
        assertThat(container.hasPlugin(pluginType), equalTo(true));
        assertThat(container.findPlugin(pluginType), sameInstance(plugin));
    }

    @Test
    public void hasAndFindPluginByTypeWithUnknownPlugin() {
        assertThat(container.hasPlugin(TestPlugin2.class), equalTo(false));
        assertThat(container.findPlugin(TestPlugin2.class), nullValue());
    }

    @Test(expected = UnknownPluginException.class)
    public void getNonUsedPluginById() {
        container.getPlugin(pluginId);
    }

    @Test(expected = UnknownPluginException.class)
    public void getNonUsedPluginByType() {
        container.getPlugin(TestPlugin1.class);
    }
}
