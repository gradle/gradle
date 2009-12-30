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
public class DefaultProjectsPluginContainerTest extends AbstractPluginContainerTest {
    protected JUnit4Mockery context = new JUnit4Mockery();
    private final DefaultProject project = HelperUtil.createRootProject();

    protected PluginRegistry pluginRegistryStub = context.mock(PluginRegistry.class);
    private DefaultProjectsPluginContainer projectsPluginHandler = new DefaultProjectsPluginContainer(pluginRegistryStub, project);

    protected TestPlugin1 pluginWithIdMock = new TestPlugin1();
    protected TestPlugin2 pluginWithoutIdMock = new TestPlugin2();

    protected DefaultProjectsPluginContainer getPluginContainer() {
        return projectsPluginHandler;
    }

    protected Plugin getPluginWithId() {
        return pluginWithIdMock;
    }

    protected Plugin getPluginWithoutId() {
        return pluginWithoutIdMock;
    }

    protected Plugin addWithType(Class<? extends Plugin> type) {
        return getPluginContainer().usePlugin(type);
    }

    protected Plugin addWithId(String pluginId) {
        return getPluginContainer().usePlugin(pluginId);
    }

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
    public void getNonUsedPluginByTyoe() {
        projectsPluginHandler.getPlugin(TestPlugin1.class);
    }
}
