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

package org.gradle.plugin.use.internal;

import org.gradle.api.Nullable;
import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.use.PluginId;

public class DefaultConfigurablePluginRequest implements InternalPluginRequest, ConfigurablePluginRequest {

    private final PluginId id;
    private String version;
    private final boolean apply;
    private final int lineNumber;
    private final String scriptDisplayName;
    private Object artifact;

    public DefaultConfigurablePluginRequest(InternalPluginRequest origonalRequest) {
        this.id = origonalRequest.getId();
        this.apply = origonalRequest.isApply();
        this.lineNumber = origonalRequest.getLineNumber();
        this.scriptDisplayName = origonalRequest.getScriptDisplayName();
        this.version = origonalRequest.getVersion();
        this.artifact = origonalRequest.getArtifact();
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
    public Object getArtifact() {
        return artifact;
    }

    @Override
    public void setArtifact(Object artifact) {
        this.artifact = artifact;
    }

    @Override
    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public boolean isApply() {
        return apply;
    }

    @Override
    public int getLineNumber() {
        return lineNumber;
    }

    @Override
    public String getScriptDisplayName() {
        return scriptDisplayName;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("[id: '").append(getId()).append("'");
        if (version != null) {
            b.append(", version: '").append(version).append("'");
        }
        if (!isApply()) {
            b.append(", apply: false");
        }

        b.append(", enriched: true");

        b.append("]");
        return b.toString();
    }

    @Override
    public String getDisplayName() {
        return toString();
    }

    public InternalPluginRequest toImmutableRequest() {
        return new DefaultPluginRequest(this);
    }

    @Override
    public boolean isEnriched() {
        return true;
    }
}
