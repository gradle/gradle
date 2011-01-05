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
            allowing(pluginRegistryStub).loadPlugin(TestPlugin1.class); will(returnValue(pluginWithIdMock));
            allowing(pluginRegistryStub).loadPlugin(TestPlugin2.class); will(returnValue(pluginWithoutIdMock));
        }});
    }

    @Test
    public void usePluginById() {
        Plugin addedPlugin = projectsPluginHandler.apply(pluginId);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.apply(pluginId), sameInstance(addedPlugin));

        assertThat(projectsPluginHandler.findPlugin(addedPlugin.getClass()), sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.findPlugin(pluginId), sameInstance(addedPlugin));
    }

    @Test
    public void usePluginWithIdByType() {
        Class<? extends Plugin> type = pluginWithIdMock.getClass();

        Plugin addedPlugin = projectsPluginHandler.apply(type);
        assertThat(pluginWithIdMock, sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.apply(type), sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.apply(pluginId), sameInstance(addedPlugin));

        assertThat(projectsPluginHandler.findPlugin(type), sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.findPlugin(pluginId), sameInstance(addedPlugin));
    }

    @Test
    public void usePluginWithoutId() {
        Class<? extends Plugin> type = pluginWithoutIdMock.getClass();
        Plugin addedPlugin = projectsPluginHandler.apply(type);
        assertThat(pluginWithoutIdMock, sameInstance(addedPlugin));
        assertThat(projectsPluginHandler.apply(type), sameInstance(addedPlugin));

        assertThat(projectsPluginHandler.findPlugin(type), sameInstance(addedPlugin));
    }

    @Test
    public void hasAndFindForPluginWithId() {
        projectsPluginHandler.apply(pluginId);
        assertThat(projectsPluginHandler.hasPlugin(pluginId), equalTo(true));
        assertThat(projectsPluginHandler.hasPlugin(pluginWithIdMock.getClass()), equalTo(true));
        assertThat(projectsPluginHandler.findPlugin(pluginId), sameInstance((Plugin) pluginWithIdMock));
        assertThat(projectsPluginHandler.findPlugin(pluginWithIdMock.getClass()), sameInstance((Plugin) pluginWithIdMock));
    }

    @Test
    public void hasAndFindForPluginWithoutId() {
        Plugin plugin = pluginWithoutIdMock;
        Class<? extends Plugin> pluginType = plugin.getClass();
        projectsPluginHandler.apply(pluginType);
        assertThat(projectsPluginHandler.hasPlugin(pluginType), equalTo(true));
        assertThat(projectsPluginHandler.findPlugin(pluginType), sameInstance(plugin));
    }

    @Test
    public void hasAndFindPluginByTypeWithUnknownPlugin() {
        assertThat(projectsPluginHandler.hasPlugin(TestPlugin2.class), equalTo(false));
        assertThat(projectsPluginHandler.findPlugin(TestPlugin2.class), nullValue());
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
