/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.tooling.events.configuration.internal;

import org.gradle.tooling.events.PluginIdentifier;
import org.gradle.tooling.events.configuration.ProjectConfigurationOperationResult.PluginConfigurationResult;

import java.time.Duration;

public class DefaultPluginConfigurationResult implements PluginConfigurationResult {

    private final PluginIdentifier plugin;
    private final Duration duration;

    public DefaultPluginConfigurationResult(PluginIdentifier plugin, Duration duration) {
        this.plugin = plugin;
        this.duration = duration;
    }

    @Override
    public PluginIdentifier getPlugin() {
        return plugin;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

}
