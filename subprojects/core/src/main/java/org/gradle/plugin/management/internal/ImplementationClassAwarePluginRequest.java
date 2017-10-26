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

public class ImplementationClassAwarePluginRequest extends DefaultPluginRequest implements PluginImplementationAware {

    private final String pluginClass;

    public ImplementationClassAwarePluginRequest(PluginId id, String version, boolean apply, Integer lineNumber, String scriptDisplayName, ModuleVersionSelector artifact, String pluginClass) {
        super(id, version, apply, lineNumber, scriptDisplayName, artifact);
        assert pluginClass != null : "pluginClass cannot be null";
        this.pluginClass = pluginClass;
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        b.append("[id: '").append(getId()).append("'");
        if (getVersion() != null) {
            b.append(", version: '").append(getVersion()).append("'");
        }
        if (getModule() != null) {
            b.append(", artifact: '").append(getModule()).append("'");
        }
        if (!isApply()) {
            b.append(", apply: false");
        }
        b.append(", plugin implementation class: '").append(pluginClass).append("'");

        b.append("]");
        return b.toString();
    }

    public String getDisplayName() {
        return toString();
    }

    @Override
    public String getPluginClass() {
        return pluginClass;
    }
}
