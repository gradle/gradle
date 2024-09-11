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
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;
import java.util.Optional;

public class DefaultPluginRequest implements PluginRequestInternal {

    private final PluginId id;
    private final String version;
    private final boolean apply;
    private final Integer lineNumber;
    private final String scriptDisplayName;
    private final ModuleVersionSelector module;
    private final PluginRequest originalRequest;
    private final Origin origin;
    private final PluginCoordinates alternativeCoordinates;

    public DefaultPluginRequest(
        PluginId id,
        boolean apply,
        Origin origin,
        @Nullable String scriptDisplayName,
        @Nullable Integer lineNumber,
        @Nullable String version,
        @Nullable ModuleVersionSelector module,
        @Nullable PluginRequest originalRequest,
        @Nullable PluginCoordinates alternativeCoordinates
    ) {
        this.id = id;
        this.version = version;
        this.apply = apply;
        this.lineNumber = lineNumber;
        this.scriptDisplayName = scriptDisplayName;
        this.module = module;
        this.originalRequest = originalRequest;
        this.origin = origin;
        this.alternativeCoordinates = alternativeCoordinates;
    }

    @Override
    public PluginId getId() {
        return id;
    }

    @Nullable
    @Override
    public String getVersion() {
        return version;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return module;
    }

    @Override
    public boolean isApply() {
        return apply;
    }

    @Nullable
    @Override
    public Integer getLineNumber() {
        return lineNumber;
    }

    @Nullable
    @Override
    public String getScriptDisplayName() {
        return scriptDisplayName;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        StringBuilder b = new StringBuilder();
        b.append("[id: '").append(id).append("'");
        if (version != null) {
            b.append(", version: '").append(version).append("'");
        }
        if (module != null) {
            b.append(", artifact: '").append(module).append("'");
        }
        if (!apply) {
            b.append(", apply: false");
        }

        b.append("]");
        return b.toString();
    }

    @Nullable
    @Override
    public PluginRequest getOriginalRequest() {
        return originalRequest;
    }

    @Override
    public Origin getOrigin() {
        return origin;
    }

    @Override
    public Optional<PluginCoordinates> getAlternativeCoordinates() {
        return Optional.ofNullable(alternativeCoordinates);
    }
}
