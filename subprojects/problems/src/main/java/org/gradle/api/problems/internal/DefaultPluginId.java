/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;
import org.gradle.api.problems.interfaces.PluginId;

import javax.annotation.Nullable;


public class DefaultPluginId implements PluginId {
    private static final String SEPARATOR = ".";
    private final String value;

    public DefaultPluginId(String value) {
        this.value = value;
    }

    private boolean isQualified() {
        return value.contains(SEPARATOR);
    }

    @Override
    @Nullable
    public String getNamespace() {
        return isQualified() ? value.substring(0, value.lastIndexOf(SEPARATOR)) : null;
    }

    @Override
    public String getName() {
        return isQualified() ? value.substring(value.lastIndexOf(SEPARATOR) + 1) : value;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public String getId() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultPluginId pluginId = (DefaultPluginId) o;

        return value.equals(pluginId.value);

    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
