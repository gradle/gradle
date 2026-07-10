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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector;
import org.gradle.plugin.management.PluginRequest;
import org.gradle.plugin.use.PluginId;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class DefaultPluginRequest implements PluginRequestInternal {

    private final PluginId id;
    private final @Nullable String version;
    private final boolean apply;
    private final @Nullable Integer lineNumber;
    private final @Nullable String scriptDisplayName;
    private final @Nullable ComponentSelector selector;
    private final @Nullable PluginRequest originalRequest;
    private final Origin origin;
    private final @Nullable PluginCoordinates alternativeCoordinates;

    public DefaultPluginRequest(
        PluginId id,
        boolean apply,
        Origin origin,
        @Nullable String scriptDisplayName,
        @Nullable Integer lineNumber,
        @Nullable String version,
        @Nullable ComponentSelector selector,
        @Nullable PluginRequest originalRequest,
        @Nullable PluginCoordinates alternativeCoordinates
    ) {
        this.id = id;
        this.version = version;
        this.apply = apply;
        this.lineNumber = lineNumber;
        this.scriptDisplayName = scriptDisplayName;
        this.selector = selector;
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

    @Override
    public @Nullable ComponentSelector getSelector() {
        return selector;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        if (selector instanceof ModuleComponentSelector) {
            return DefaultModuleVersionSelector.newSelector((ModuleComponentSelector) selector);
        }
        return null;
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
        if (selector != null) {
            b.append(", artifact: '").append(selector).append("'");
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
