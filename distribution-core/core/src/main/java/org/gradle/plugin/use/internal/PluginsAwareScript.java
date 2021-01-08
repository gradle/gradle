/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.plugin.use.internal;

import groovy.lang.Closure;
import org.gradle.groovy.scripts.DefaultScript;
import org.gradle.plugin.management.internal.PluginRequests;
import org.gradle.plugin.use.PluginDependenciesSpec;
import org.gradle.util.ConfigureUtil;

abstract public class PluginsAwareScript extends DefaultScript {

    private PluginRequestCollector pluginRequestCollector;

    public void plugins(int lineNumber, Closure configureClosure) {
        if (pluginRequestCollector == null) {
            pluginRequestCollector = new PluginRequestCollector(getScriptSource());
        }
        PluginDependenciesSpec spec = pluginRequestCollector.createSpec(lineNumber);
        ConfigureUtil.configure(configureClosure, spec);
    }

    public PluginRequests getPluginRequests() {
        if (pluginRequestCollector != null) {
            return pluginRequestCollector.getPluginRequests();
        }
        return PluginRequests.EMPTY;
    }

}
