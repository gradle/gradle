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

package org.gradle.plugin.management.internal.argumentloaded;

import com.google.common.base.Strings;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.management.internal.PluginCoordinates;
import org.gradle.plugin.management.internal.PluginRequestInternal;
import org.gradle.plugin.use.PluginId;
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;
import java.util.Optional;

/**
 * A {@link PluginRequest} that represents a plugin request added outside any build script.
 * <p>
 * These originate from an id + version pair in the form of {@code id:version}.  These are currently
 * parsed from a system property by {@link org.gradle.plugin.management.internal.PluginHandler#getArgumentSourcedPlugins()}.
 */
public final class ArgumentSourcedPluginRequest implements PluginRequestInternal {
    private static final int PLUGIN_ID_INDEX = 0;
    private static final int PLUGIN_VERSION_INDEX = 1;

    private final PluginId id;
    private final String version;

    public static ArgumentSourcedPluginRequest parsePluginRequest(String pluginCoords) {
        String[] parts = pluginCoords.split(":");
        if (parts.length != 2 || Strings.isNullOrEmpty(parts[0]) || Strings.isNullOrEmpty(parts[1])) {
            throw new IllegalArgumentException(String.format("Invalid plugin format: '%s'. Expected format is 'id:version'.", pluginCoords));
        }
        return new ArgumentSourcedPluginRequest(parts[PLUGIN_ID_INDEX], parts[PLUGIN_VERSION_INDEX]);
    }

    public ArgumentSourcedPluginRequest(String pluginId, String pluginVersion) {
        this.id = DefaultPluginId.of(pluginId);
        this.version = pluginVersion;
    }

    @Override
    public boolean isApply() {
        return true; // Argument-loaded plugins are automatically applied
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
        return String.format("[id: '%s', version: '%s', apply: %s]", id, version, isApply());
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
