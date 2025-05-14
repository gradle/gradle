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

package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalBinaryPluginIdentifier;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class DefaultBinaryPluginIdentifier implements InternalBinaryPluginIdentifier, Serializable {

    private final String displayName;
    private final String className;
    private final String pluginId;

    public DefaultBinaryPluginIdentifier(String displayName, String className, @Nullable String pluginId) {
        this.displayName = displayName;
        this.className = className;
        this.pluginId = pluginId;
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getPluginId() {
        return pluginId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultBinaryPluginIdentifier that = (DefaultBinaryPluginIdentifier) o;
        return className.equals(that.className);
    }

    @Override
    public int hashCode() {
        return className.hashCode();
    }

}
