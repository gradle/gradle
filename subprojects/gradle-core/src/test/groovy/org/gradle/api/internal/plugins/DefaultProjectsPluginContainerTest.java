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
import static org.junit.Assert.*;

/**
 * @author Hans Dockter
 */
public class DefaultProjectsPluginContainerTest {
    protected String pluginId = "somePluginId";
    protected JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultProject project = HelperUtil.createRootProject();

    private PluginRegistry pluginRegistryStub = context.mock(PluginRegistry.class);
    private DefaultProjectsPluginContainer projectsPluginHandler = new DefaultProjectsPluginContainer(pluginRegistryStub, project);

    private TestPlugin1 pluginWithIdMock = new TestPlugin1();
    private TestPlugin2 pluginWithoutIdMock = new TestPlugin2();

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(pluginRegistryStub).getTypeForId(pluginId); will(returnValue(TestPlugin1.class));
            allowing(pluginRegistryStub).getNameForType(TestPlugin1.class); will(returnValue(pluginId));
            allowing(pluginRegistryStub).loadPlugin(TestPlugin1.class); will(returnValue(pluginWithIdMock));
            allowing(pluginRegistryStub).loadPlugin(TestPlugin2.class); will(returnValue(pluginWithoutIdMock));
            allowing(pluginRegistryStub).getNameForType(TestPlugin2.class); will(returnValue(TestPlugin2.class.getName()));
        }});
    }

    @Test
    public void addPluginByType() {
        Plugin addedPlugin = projectsPluginHandler.usePlugin(pluginId);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance(projectsPluginHandler.usePlugin(pluginId)));
        assertThat(addedPlugin, sameInstance(projectsPluginHandler.findByName(pluginId)));
    }

    @Test
    public void addPluginWithIdByType() {
        Class<? extends Plugin> type = pluginWithIdMock.getClass();
        Plugin addedPlugin = projectsPluginHandler.usePlugin(type);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance((Plugin) projectsPluginHandler.usePlugin(type)));
        assertThat(addedPlugin, sameInstance(projectsPluginHandler.usePlugin(pluginId)));
        assertThat(addedPlugin, sameInstance(projectsPluginHandler.findByName(pluginId)));
    }

    @Test
    public void addPluginWithoutId() {
        Class<? extends Plugin> type = pluginWithoutIdMock.getClass();
        Plugin addedPlugin = projectsPluginHandler.usePlugin(type);
        assertThat(pluginWithoutIdMock, sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance((Plugin) projectsPluginHandler.usePlugin(type)));
        assertThat(addedPlugin, sameInstance(projectsPluginHandler.findByName(type.getName())));
    }

    @Test
    public void hasAndFindForPluginWithId() {
        projectsPluginHandler.usePlugin(pluginId);
        assertThat(projectsPluginHandler.hasPlugin(pluginId), equalTo(true));
        assertThat(projectsPluginHandler.hasPlugin(pluginWithIdMock.getClass()), equalTo(true));
        assertThat((TestPlugin1) projectsPluginHandler.findPlugin(pluginId),
                sameInstance((Plugin) pluginWithIdMock));
        assertThat((TestPlugin1) projectsPluginHandler.findPlugin(pluginWithIdMock.getClass()),
                sameInstance((Plugin) pluginWithIdMock));
    }

    @Test
    public void hasAndFindForPluginWithoutId() {
        Plugin plugin = pluginWithoutIdMock;
        Class<? extends Plugin> pluginType = plugin.getClass();
        projectsPluginHandler.usePlugin(pluginType);
        assertThat(projectsPluginHandler.hasPlugin(pluginType.getName()), equalTo(true));
        assertThat(projectsPluginHandler.hasPlugin(pluginType), equalTo(true));
        assertThat(projectsPluginHandler.findPlugin(pluginType), sameInstance(plugin));
        assertThat(projectsPluginHandler.findPlugin(pluginType.getName()), sameInstance(plugin));
    }

    @Test
    public void hasAndFindPluginByTypeWithUnknownPlugin() {
        assertThat(projectsPluginHandler.hasPlugin(TestPlugin2.class), equalTo(false));
        assertThat(projectsPluginHandler.findPlugin(TestPlugin2.class), nullValue());
    }

    @Test
    public void hasAndFindPluginByIdWithUnknownPlugin() {
        String unknownId = pluginId + "x";
        assertThat(projectsPluginHandler.hasPlugin(unknownId), equalTo(false));
        assertThat(projectsPluginHandler.findPlugin(unknownId), nullValue());
    }

    @org.junit.Test
    public void usePluginWithId() {
        projectsPluginHandler.usePlugin(pluginId);
        projectsPluginHandler.usePlugin(pluginId);
        assertThat(pluginWithIdMock.getApplyCounter(), equalTo(1));
        assertThat((TestPlugin1) projectsPluginHandler.getPlugin(pluginId), sameInstance(pluginWithIdMock));
    }

    @org.junit.Test
    public void usePluginWithType() {
        projectsPluginHandler.usePlugin(TestPlugin1.class);
        projectsPluginHandler.usePlugin(TestPlugin1.class);
        assertThat(pluginWithIdMock.getApplyCounter(), equalTo(1));
        assertThat((TestPlugin1) projectsPluginHandler.getPlugin(TestPlugin1.class), sameInstance(pluginWithIdMock));
    }

    @Test(expected = UnknownPluginException.class)
    public void getNonUsedPluginById() {
        projectsPluginHandler.getPlugin(pluginId);
    }

    @Test(expected = UnknownPluginException.class)
    public void getNonUsedPluginByType() {
        projectsPluginHandler.getPlugin(TestPlugin1.class);
    }
}
