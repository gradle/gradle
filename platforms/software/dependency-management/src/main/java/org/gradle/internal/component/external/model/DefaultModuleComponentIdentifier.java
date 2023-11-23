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
package org.gradle.internal.component.external.model;

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.internal.DisplayName;

public class DefaultModuleComponentIdentifier implements ModuleComponentIdentifier, DisplayName {
    private final ModuleIdentifier moduleIdentifier;
    private final String version;
    private final int hashCode;

    public DefaultModuleComponentIdentifier(ModuleIdentifier module, String version) {
        assert module != null : "module cannot be null";
        assert module.getGroup() != null : "group cannot be null";
        assert module.getName() != null : "name cannot be null";
        assert version != null : "version cannot be null";
        this.moduleIdentifier = module;
        this.version = version;
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        this.hashCode = 31 * version.hashCode() + module.hashCode();
    }

    @Override
    public String getDisplayName() {
        String group = moduleIdentifier.getGroup();
        String module = moduleIdentifier.getName();
        StringBuilder builder = new StringBuilder(group.length() + module.length() + version.length() + 2);
        builder.append(group);
        builder.append(":");
        builder.append(module);
        builder.append(":");
        builder.append(version);
        return builder.toString();
    }

    @Override
    public String getCapitalizedDisplayName() {
        return getDisplayName();
    }

    @Override
    public String getGroup() {
        return moduleIdentifier.getGroup();
    }

    @Override
    public String getModule() {
        return moduleIdentifier.getName();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return moduleIdentifier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleComponentIdentifier that = (DefaultModuleComponentIdentifier) o;

        if (hashCode != that.hashCode) {
            return false;
        }
        if (!moduleIdentifier.equals(that.moduleIdentifier)) {
            return false;
        }
        return version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentIdentifier newId(ModuleIdentifier module, String version) {
        return new DefaultModuleComponentIdentifier(module, version);
    }

    public static ModuleComponentIdentifier newId(ModuleVersionIdentifier moduleVersionIdentifier) {
        return new DefaultModuleComponentIdentifier(moduleVersionIdentifier.getModule(), moduleVersionIdentifier.getVersion());
    }
}

