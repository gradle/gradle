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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.internal.build.event.types.DefaultBinaryPluginIdentifier;
import org.gradle.internal.build.event.types.DefaultScriptPluginIdentifier;
import org.gradle.internal.code.UserCodeSource;
import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalPluginIdentifier;
import org.gradle.tooling.internal.protocol.events.InternalScriptPluginIdentifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Objects;

@NullMarked
public class PluginIdentifiers {

    /**
     * Converts a {@link UserCodeSource} into an {@link InternalPluginIdentifier}.
     *
     * @return null if the source cannot be represented as a plugin identifier.
     */
    public static @Nullable InternalPluginIdentifier toInternalPluginIdentifier(UserCodeSource source) {
        if (source instanceof UserCodeSource.Binary binary) {
            return toInternalBinaryPluginIdentifier(binary);
        } else if (source instanceof UserCodeSource.Script scriptSource) {
            if (scriptSource.getUri() != null) {
                return toInternalScriptPluginIdentifier(scriptSource);
            }
        }

        return null;
    }

    private static InternalBinaryPluginIdentifier toInternalBinaryPluginIdentifier(UserCodeSource.Binary source) {
        String className = source.getClassName();
        String pluginId = source.getPluginId();
        String displayName = pluginId != null ? pluginId : className;
        return new DefaultBinaryPluginIdentifier(displayName, className, pluginId);
    }

    private static InternalScriptPluginIdentifier toInternalScriptPluginIdentifier(UserCodeSource.Script source) {
        URI uri = Objects.requireNonNull(source.getUri());
        String path = uri.getPath() != null ? uri.getPath() : uri.getSchemeSpecificPart();
        String displayName = path.substring(path.lastIndexOf('/') + 1);
        return new DefaultScriptPluginIdentifier(displayName, uri);
    }

}
