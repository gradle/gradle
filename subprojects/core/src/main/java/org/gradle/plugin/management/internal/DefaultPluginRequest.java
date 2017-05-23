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
import org.gradle.plugin.use.internal.DefaultPluginId;

import javax.annotation.Nullable;

public class DefaultPluginRequest implements PluginRequestInternal {

    private final String requestingScriptDisplayName;
    private final int requestingScriptLineNumber;

    private final PluginId id;
    private final String version;
    private final String script;
    private final boolean apply;
    private final ModuleVersionSelector artifact;

    public DefaultPluginRequest(ScriptSource requestingScriptSource, int requestingScriptLineNumber,
                                PluginId id, String version, String script, boolean apply) {
        this(
            requestingScriptSource.getDisplayName(), requestingScriptLineNumber,
            id, version, script, apply, null);
    }

    // Used for testing only
    public DefaultPluginRequest(String requestingScriptDisplayName, int requestingScriptLineNumber,
                                String id, String version, String script, boolean apply) {
        this(
            requestingScriptDisplayName, requestingScriptLineNumber,
            id == null ? null : DefaultPluginId.of(id), version, script, apply, null);
    }

    // Used for serialization/copy/mutation and testing
    public DefaultPluginRequest(String requestingScriptDisplayName, int requestingScriptLineNumber,
                                PluginId id, String version, String script, boolean apply, ModuleVersionSelector artifact) {

        this.requestingScriptDisplayName = requestingScriptDisplayName;
        this.requestingScriptLineNumber = requestingScriptLineNumber;

        this.id = id;
        this.version = version;
        this.script = script;
        this.apply = apply;
        this.artifact = artifact;
    }

    @Override
    public int getRequestingScriptLineNumber() {
        return requestingScriptLineNumber;
    }

    @Override
    public String getRequestingScriptDisplayName() {
        return requestingScriptDisplayName;
    }

    @Nullable
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
    public String getScript() {
        return script;
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

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return buildDisplayName(id, script, version, artifact, apply);
    }

    public static String buildDisplayName(PluginId id, String script, String version, ModuleVersionSelector artifact, boolean apply) {
        StringBuilder b = new StringBuilder();
        if (id != null) {
            b.append("id '").append(id).append("'");
        } else {
            b.append("script '").append(script).append("'");
        }
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
