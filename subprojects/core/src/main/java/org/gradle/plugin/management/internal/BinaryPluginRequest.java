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
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.plugin.use.PluginId;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class BinaryPluginRequest extends AbstractPluginRequest {

    private final PluginId id;
    private final String version;
    private final boolean apply;
    private final ModuleVersionSelector artifact;

    public BinaryPluginRequest(ScriptSource requestingScriptSource, int requestingScriptLineNumber, PluginId id, String version, boolean apply, ModuleVersionSelector artifact) {
        this(requestingScriptSource.getDisplayName(), requestingScriptLineNumber, id, version, apply, artifact);
    }

    public BinaryPluginRequest(String requestingScriptDisplayName, int requestingScriptLineNumber, PluginId id, String version, boolean apply, ModuleVersionSelector artifact) {
        super(requestingScriptDisplayName, requestingScriptLineNumber);
        this.id = checkNotNull(id);
        this.version = version;
        this.apply = apply;
        this.artifact = artifact;
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
    public boolean isApply() {
        return apply;
    }

    @Nullable
    @Override
    public ModuleVersionSelector getModule() {
        return artifact;
    }

    @Override
    public String getDisplayName() {
        StringBuilder b = new StringBuilder();
        b.append("id '").append(id).append("'");
        if (version != null) {
            b.append(" version '").append(version).append("'");
        }
        if (artifact != null) {
            b.append(" artifact '").append(artifact).append("'");
        }
        if (!apply) {
            b.append(" apply false");
        }
        return b.toString();
    }
}
