/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.internal.plugins.PluginAwareInternal;
import org.gradle.api.plugins.ObjectConfigurationAction;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.configuration.ConfigurationTargetIdentifier;
import org.gradle.util.ConfigureUtil;

import javax.inject.Inject;
import java.util.Map;

public abstract class AbstractPluginAware implements PluginAwareInternal {

    public void apply(Closure closure) {
        apply(ConfigureUtil.configureUsing(closure));
    }

    public void apply(Action<? super ObjectConfigurationAction> action) {
        DefaultObjectConfigurationAction configAction = createObjectConfigurationAction();
        action.execute(configAction);
        configAction.execute();
    }

    public void apply(Map<String, ?> options) {
        DefaultObjectConfigurationAction action = createObjectConfigurationAction();
        ConfigureUtil.configureByMap(options, action);
        action.execute();
    }

    public PluginContainer getPlugins() {
        return getPluginManager().getPluginContainer();
    }

    @Inject
    public ConfigurationTargetIdentifier getConfigurationTargetIdentifier() {
        throw new UnsupportedOperationException();
    }

    abstract protected DefaultObjectConfigurationAction createObjectConfigurationAction();

}
