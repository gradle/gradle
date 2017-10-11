/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

public class DefaultPluginRequest implements PluginRequestInternal {

    private final PluginId id;
    private final String version;
    private final boolean apply;
    private final String origin;
    private final ModuleVersionSelector artifact;
    private final PluginRequestInternal originalRequest;

    public DefaultPluginRequest(PluginId id, String version, boolean apply, String origin) {
        this(id, version, apply, origin, null);
    }

    public DefaultPluginRequest(PluginId id, String version, boolean apply, String origin, ModuleVersionSelector artifact) {
        this(id, version, apply, origin, artifact, null);
    }

    public DefaultPluginRequest(PluginId id, String version, boolean apply, String origin, ModuleVersionSelector artifact,
                                PluginRequestInternal originalRequest) {
        this.id = id;
        this.version = version;
        this.apply = apply;
        this.origin = origin;
        this.artifact = artifact;
        this.originalRequest = originalRequest != null ? originalRequest : this;
    }

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
        return artifact;
    }

    @Override
    public boolean isApply() {
        return apply;
    }

    public String getOrigin() {
        return origin;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("[id: '").append(id).append("'");
        if (version != null) {
            b.append(", version: '").append(version).append("'");
        }
        if (artifact != null) {
            b.append(", artifact: '").append(artifact).append("'");
        }
        if (!apply) {
            b.append(", apply: false");
        }

        b.append("]");
        return b.toString();
    }

    public String getDisplayName() {
        return toString();
    }

    @Override
    public PluginRequestInternal getOriginalRequest() {
        return originalRequest;
    }
}
