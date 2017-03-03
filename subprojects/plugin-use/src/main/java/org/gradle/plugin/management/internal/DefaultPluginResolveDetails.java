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

package org.gradle.plugin.management.internal;

import org.gradle.api.Action;
import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.PluginResolveDetails;

public class DefaultPluginResolveDetails implements PluginResolveDetails {

    private final InternalPluginRequest pluginRequest;
    private InternalPluginRequest targetPluginRequest;

    public DefaultPluginResolveDetails(InternalPluginRequest pluginRequest) {
        this.pluginRequest = pluginRequest;
        this.targetPluginRequest = pluginRequest;
    }

    @Override
    public PluginRequest getRequested() {
        return pluginRequest;
    }

    @Override
    public void useTarget(Action<? super ConfigurablePluginRequest> action) {
        DefaultConfigurablePluginRequest cfg = new DefaultConfigurablePluginRequest(pluginRequest);
        action.execute(cfg);
        targetPluginRequest = cfg.toImmutableRequest();
    }

    @Override
    public InternalPluginRequest getTarget() {
        return targetPluginRequest;
    }
}
