/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.plugin.internal.PluginId;

public class DefaultPluginRequest implements PluginRequest {

    private final PluginId id;
    private final String version;
    private final boolean apply;
    private final int lineNumber;
    private final String scriptDisplayName;

    public DefaultPluginRequest(String id, String version, boolean apply, int lineNumber, ScriptSource scriptSource) {
        this(PluginId.of(id), version, apply, lineNumber, scriptSource);
    }

    public DefaultPluginRequest(PluginId id, String version, boolean apply, int lineNumber, ScriptSource scriptSource) {
        this(id, version, apply, lineNumber, scriptSource.getDisplayName());
    }

    public DefaultPluginRequest(String id, String version, boolean apply, int lineNumber, String scriptDisplayName) {
        this(PluginId.of(id), version, apply, lineNumber, scriptDisplayName);
    }

    public DefaultPluginRequest(PluginId id, String version, boolean apply, int lineNumber, String scriptDisplayName) {
        this.id = id;
        this.version = version;
        this.apply = apply;
        this.lineNumber = lineNumber;
        this.scriptDisplayName = scriptDisplayName;
    }

    @Override
    public PluginId getId() {
        return id;
    }

    @Override
    public String getVersion() {
        return version;
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
        b.append("[id: '").append(id).append("'");
        if (version != null) {
            b.append(", version: '").append(version).append("'");
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultPluginRequest that = (DefaultPluginRequest) o;

        if (apply != that.apply) {
            return false;
        }
        if (!id.equals(that.id)) {
            return false;
        }
        return version != null ? version.equals(that.version) : that.version == null;

    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        result = 31 * result + (apply ? 1 : 0);
        return result;
    }
}
