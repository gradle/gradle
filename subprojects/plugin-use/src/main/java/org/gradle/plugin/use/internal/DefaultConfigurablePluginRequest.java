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
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.internal.artifacts.dsl.ModuleVersionSelectorParsers;
import org.gradle.plugin.management.ConfigurablePluginRequest;
import org.gradle.plugin.use.PluginId;

public class DefaultConfigurablePluginRequest implements InternalPluginRequest, ConfigurablePluginRequest {

    private final PluginId id;
    private String version;
    private final boolean apply;
    private final int lineNumber;
    private final String scriptDisplayName;
    private ModuleVersionSelector artifact;
    private Object artifactNotation;

    public DefaultConfigurablePluginRequest(InternalPluginRequest originalRequest) {
        this.id = originalRequest.getId();
        this.apply = originalRequest.isApply();
        this.lineNumber = originalRequest.getLineNumber();
        this.scriptDisplayName = originalRequest.getScriptDisplayName();
        this.version = originalRequest.getVersion();
        this.artifact = originalRequest.getArtifact();
        this.artifactNotation = originalRequest.getArtifactNotation();
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
    public ModuleVersionSelector getArtifact() {
        return artifact;
    }

    @Override
    public void setArtifact(Object artifact) {
        this.artifactNotation = artifact;
        this.artifact = ModuleVersionSelectorParsers.parser().parseNotation(artifact);
    }

    @Override
    public Object getArtifactNotation() {
        return artifactNotation;
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
}
