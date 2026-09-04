/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider.provenance;

import org.gradle.internal.code.UserCodeSource;
import org.jspecify.annotations.Nullable;

/**
 * Shared diagnostic descriptor, independent of the operation and optional source location.
 * Contains no application, plugin instance, class loader, or property owner.
 *
 * <p>The identifier is not a globally unique contributor key or proof of authority. In particular,
 * a plugin ID does not distinguish applications in different builds. Script roles and build identity
 * must eventually come from their application boundaries, not from parsing display names.</p>
 */
public final class PropertyProvenanceOrigin {
    public enum Type {
        PLUGIN_ID,
        PLUGIN_CLASS,
        SCRIPT,
        DESCRIPTION,
        UNKNOWN
    }

    public static final PropertyProvenanceOrigin UNKNOWN = new PropertyProvenanceOrigin(Type.UNKNOWN, null, "unknown code");

    private final Type type;
    private final @Nullable String identifier;
    private final String displayName;

    private PropertyProvenanceOrigin(Type type, @Nullable String identifier, String displayName) {
        this.type = type;
        this.identifier = identifier;
        this.displayName = displayName;
    }

    public static PropertyProvenanceOrigin from(UserCodeSource source) {
        String displayName = source.getDisplayName().getDisplayName();
        if (source instanceof UserCodeSource.Binary) {
            UserCodeSource.Binary binary = (UserCodeSource.Binary) source;
            String pluginId = binary.getPluginId();
            return pluginId == null
                ? new PropertyProvenanceOrigin(Type.PLUGIN_CLASS, binary.getClassName(), displayName)
                : new PropertyProvenanceOrigin(Type.PLUGIN_ID, pluginId, displayName);
        }
        if (source instanceof UserCodeSource.Script) {
            UserCodeSource.Script script = (UserCodeSource.Script) source;
            return new PropertyProvenanceOrigin(Type.SCRIPT, script.getUri() == null ? null : script.getUri().normalize().toString(), displayName);
        }
        return description(displayName);
    }

    /**
     * A display-only origin, for example the task action exposing a failure. Never infer identity from it.
     */
    public static PropertyProvenanceOrigin description(String displayName) {
        return new PropertyProvenanceOrigin(Type.DESCRIPTION, null, displayName);
    }

    public Type getType() {
        return type;
    }

    public @Nullable String getIdentifier() {
        return identifier;
    }

    public String getDisplayName() {
        return displayName;
    }
}
