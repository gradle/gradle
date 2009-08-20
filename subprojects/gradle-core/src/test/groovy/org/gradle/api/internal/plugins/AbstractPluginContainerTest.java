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
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import org.junit.Test;

/**
 * @author Hans Dockter
 */
public abstract class AbstractPluginContainerTest {
    protected abstract AbstractPluginContainer getPluginContainer();

    protected String pluginId = "somePluginId";

    protected abstract Plugin getPluginWithId();
    protected abstract Plugin getPluginWithoutId();

    protected abstract Plugin addWithType(Class<? extends Plugin> type);

    protected abstract Plugin addWithId(String pluginId);

    @Test
    public void addPluginByType() {
        Plugin addedPlugin = addWithId(pluginId);
        assertThat(getPluginWithId(), sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance(addWithId(pluginId)));
        assertThat(addedPlugin, sameInstance(getPluginContainer().findByName(pluginId)));
    }

    @Test
    public void addPluginWithIdByType() {
        Class<? extends Plugin> type = getPluginWithId().getClass();
        Plugin addedPlugin = addWithType(type);
        assertThat(getPluginWithId(), sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance((Plugin) addWithType(type)));
        assertThat(addedPlugin, sameInstance(addWithId(pluginId)));
        assertThat(addedPlugin, sameInstance(getPluginContainer().findByName(pluginId)));
    }

    @Test
    public void addPluginWithoutId() {
        Class<? extends Plugin> type = getPluginWithoutId().getClass();
        Plugin addedPlugin = addWithType(type);
        assertThat(getPluginWithoutId(), sameInstance(addedPlugin));
        assertThat(addedPlugin, sameInstance((Plugin) addWithType(type)));
        assertThat(addedPlugin, sameInstance(getPluginContainer().findByName(type.getName())));
    }
    
    @Test
    public void hasAndFindForPluginWithId() {
        addWithId(pluginId);
        assertThat(getPluginContainer().hasPlugin(pluginId), equalTo(true));
        assertThat(getPluginContainer().hasPlugin(getPluginWithId().getClass()), equalTo(true));
        assertThat((TestPlugin1) getPluginContainer().findPlugin(pluginId),
                sameInstance(getPluginWithId()));
        assertThat((TestPlugin1) getPluginContainer().findPlugin(getPluginWithId().getClass()),
                sameInstance(getPluginWithId()));
    }

    @Test
    public void hasAndFindForPluginWithoutId() {
        Plugin plugin = getPluginWithoutId();
        Class<? extends Plugin> pluginType = plugin.getClass();
        addWithType(pluginType);
        assertThat(getPluginContainer().hasPlugin(pluginType.getName()), equalTo(true));
        assertThat(getPluginContainer().hasPlugin(pluginType), equalTo(true));
        assertThat(getPluginContainer().findPlugin(pluginType), sameInstance(plugin));
        assertThat(getPluginContainer().findPlugin(pluginType.getName()), sameInstance(plugin));
    }

    @Test
    public void hasAndFindPluginByTypeWithUnknownPlugin() {
        assertThat(getPluginContainer().hasPlugin(TestPlugin2.class), equalTo(false));
        assertThat(getPluginContainer().findPlugin(TestPlugin2.class), nullValue());
    }

    @Test
    public void hasAndFindPluginByIdWithUnknownPlugin() {
        String unknownId = pluginId + "x";
        assertThat(getPluginContainer().hasPlugin(unknownId), equalTo(false));
        assertThat(getPluginContainer().findPlugin(unknownId), nullValue());
    }
}
