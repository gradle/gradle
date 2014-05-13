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
    private final int lineNumber;
    private final ScriptSource scriptSource;

    public DefaultPluginRequest(String id, String version, int lineNumber, ScriptSource scriptSource) {
        this(PluginId.of(id), version, lineNumber, scriptSource);
    }

    public DefaultPluginRequest(PluginId id, String version, int lineNumber, ScriptSource scriptSource) {
        this.id = id;
        this.version = version;
        this.lineNumber = lineNumber;
        this.scriptSource = scriptSource;
    }

    public PluginId getId() {
        return id;
    }

    public String getVersion() {
        return version;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public ScriptSource getScriptSource() {
        return scriptSource;
    }

    @Override
    public String toString() {
        if (version == null) {
            return String.format("[id: '%s']", id);
        } else {
            return String.format("[id: '%s', version: '%s']", id, version);
        }
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

        if (!id.equals(that.id)) {
            return false;
        }
        if (version != null ? !version.equals(that.version) : that.version != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + (version != null ? version.hashCode() : 0);
        return result;
    }


}
