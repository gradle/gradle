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

package org.gradle.plugin.management.internal;

import com.google.common.base.Strings;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.autoapply.AutoAppliedPluginHandler;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A {@link PluginRequest} that represents a plugin request added outside of any build script.
 * <p>
 * These originate from an id + version pair in the form of {@code id:version}.  These are currently
 * parsed from a system property by {@link AutoAppliedPluginHandler#getArgumentLoadedPlugins()}.
 */
public final class ArgumentLoadedPluginRequest implements PluginRequestInternal {
    private static final int PLUGIN_ID_INDEX = 0;
    private static final int PLUGIN_VERSION_INDEX = 1;

    private final PluginId id;
    private final String version;

    public static ArgumentLoadedPluginRequest parsePluginRequest(String pluginCoords) {
        String[] parts = pluginCoords.split(":");
        if (parts.length != 2 || Strings.isNullOrEmpty(parts[0]) || Strings.isNullOrEmpty(parts[1])) {
            throw new IllegalArgumentException(String.format("Invalid plugin format: '%s'. Expected format is 'id:version'.", pluginCoords));
        }
        return new ArgumentLoadedPluginRequest(parts[PLUGIN_ID_INDEX], parts[PLUGIN_VERSION_INDEX]);
    }

    public ArgumentLoadedPluginRequest(String pluginId, String pluginVersion) {
        this.id = DefaultPluginId.of(pluginId);
        this.version = pluginVersion;
    }

    @Override
    public boolean isApply() {
        return false; // We're never applying these plugins, we're just loading them
    }

    @Nullable
    @Override
    public Integer getLineNumber() {
        return null; // We'll never have a script, thus no line number
    }

    @Nullable
    @Override
    public String getScriptDisplayName() {
        return null; // We'll never have a script, thus no script name
    }

    @Override
    public String getDisplayName() {
        return String.format("[id: '%s', version: '%s', apply: false]", id, version);
    }

    @Override
    public PluginRequest getOriginalRequest() {
        return this; // We're the original request
    }

    @Override
    public Origin getOrigin() {
        return Origin.OTHER;
    }

    @Override
    public Optional<PluginCoordinates> getAlternativeCoordinates() {
        return Optional.empty();
    }

    @Override
    public PluginId getId() {
        return id;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return null;
    }
}
