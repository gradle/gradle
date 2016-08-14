/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.initialization;

import groovy.lang.Closure;
import org.gradle.api.initialization.Settings;
import org.gradle.api.internal.ExtensibleDynamicObject;
import org.gradle.api.internal.plugins.ExtraPropertiesDynamicObjectAdapter;
import org.gradle.internal.metaobject.DynamicObject;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugin.repository.PluginRepositoriesSpec;
import org.gradle.plugin.repository.internal.DefaultPluginRepositoriesSpec;
import org.gradle.plugin.repository.internal.PluginRepositoryFactory;
import org.gradle.plugin.repository.internal.PluginRepositoryRegistry;
import org.gradle.util.ConfigureUtil;

/**
 * Endows a {@link SettingsScript} with methods which can only be used when
 * processing top-level blocks on the initial pass through a script. Restricts usage of
 * APIs that would yield confusing results at that stage. For backwards compatibility reasons,
 * this restriciton was not added for the `initscript` block.
 */
public abstract class InitialPassSettingsScript extends SettingsScript {
    private boolean inPluginRepositoriesBlock;

    private PluginRepositoriesSpec getPluginRepositorySpec() {
        Instantiator instantiator = __scriptServices.get(Instantiator.class);
        PluginRepositoryFactory pluginRepositoryFactory = __scriptServices.get(PluginRepositoryFactory.class);
        PluginRepositoryRegistry pluginRepositoryRegistry = __scriptServices.get(PluginRepositoryRegistry.class);
        return instantiator.newInstance(
            DefaultPluginRepositoriesSpec.class, pluginRepositoryFactory, pluginRepositoryRegistry, getFileResolver());
    }

    public void pluginRepositories(Closure config) {
        try {
            inPluginRepositoriesBlock = true;
            ConfigureUtil.configure(config, getPluginRepositorySpec());
        } finally {
            inPluginRepositoriesBlock = false;
        }
    }

    @Override
    protected DynamicObject getDynamicTarget() {
        if (inPluginRepositoriesBlock) {
            return new ExtraPropertiesDynamicObjectAdapter(Settings.class, ((ExtensibleDynamicObject) super.getDynamicTarget()).getDynamicProperties());
        } else {
            return super.getDynamicTarget();
        }
    }
}
