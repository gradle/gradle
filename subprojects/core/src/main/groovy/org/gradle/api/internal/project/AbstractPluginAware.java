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

package org.gradle.api.internal.project;

import groovy.lang.Closure;
import org.gradle.api.internal.plugins.DefaultObjectConfigurationAction;
import org.gradle.api.plugins.PluginAware;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

abstract public class AbstractPluginAware implements PluginAware {

    public void apply(Closure closure) {
        DefaultObjectConfigurationAction action = createObjectConfigurationAction();
        ConfigureUtil.configure(closure, action);
        action.execute();
    }

    public void apply(Map<String, ?> options) {
        DefaultObjectConfigurationAction action = createObjectConfigurationAction();
        ConfigureUtil.configureByMap(options, action);
        action.execute();
    }

    abstract protected DefaultObjectConfigurationAction createObjectConfigurationAction();

}
