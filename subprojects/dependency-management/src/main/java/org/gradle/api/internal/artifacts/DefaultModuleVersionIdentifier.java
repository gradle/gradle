/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class DefaultModuleVersionIdentifier implements ModuleVersionIdentifier {

    private final DefaultModuleIdentifier id;
    private final String version;

    public DefaultModuleVersionIdentifier(String group, String name, String version) {
        assert group != null : "group cannot be null";
        assert name != null : "name cannot be null";
        assert version != null : "version cannot be null";
        this.id = new DefaultModuleIdentifier(group, name);
        this.version = version;
    }

    public DefaultModuleVersionIdentifier(ModuleIdentifier id, String version) {
        this.id = new DefaultModuleIdentifier(id.getGroup(), id.getName());
        this.version = version;
    }

    public String getGroup() {
        return id.getGroup();
    }

    public String getName() {
        return id.getName();
    }

    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return String.format("%s:%s:%s", id.getGroup(), id.getName(), version);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        DefaultModuleVersionIdentifier other = (DefaultModuleVersionIdentifier) obj;
        if (!id.equals(other.id)) {
            return false;
        }
        if (!version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return id.hashCode() ^ version.hashCode();
    }

    public ModuleIdentifier getModule() {
        return id;
    }

    public static ModuleVersionIdentifier newId(Module module) {
        return new DefaultModuleVersionIdentifier(module.getGroup(), module.getName(), module.getVersion());
    }

    public static ModuleVersionIdentifier newId(String group, String name, String version) {
        return new DefaultModuleVersionIdentifier(group, name, version);
    }

    public static ModuleVersionIdentifier newId(ModuleRevisionId moduleRevisionId) {
        return new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
    }

    public static ModuleVersionIdentifier newId(ModuleComponentIdentifier componentId) {
        return new DefaultModuleVersionIdentifier(componentId.getGroup(), componentId.getModule(), componentId.getVersion());
    }
}
