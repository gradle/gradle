/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.initialization.dsl;

import org.gradle.api.initialization.dsl.BuildSettings;
import org.gradle.api.internal.plugins.PluginRegistry;
import org.gradle.plugin.management.internal.PluginRequests;

import java.util.List;
import java.util.Map;

public interface BuildSettingsInternal extends BuildSettings {
    PluginRequests getRegisteredPluginRequests();

    Map<String, DeclarativeExtension> getDeclarativeExtensions(PluginRegistry pluginRegistry);
}
