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

import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class DefaultModuleVersionIdentifier implements ModuleVersionIdentifier {

    private final ModuleIdentifier id;
    private final String version;
    private final int hashCode;

    private DefaultModuleVersionIdentifier(String group, String name, String version) {
        assert group != null : "group cannot be null";
        assert name != null : "name cannot be null";
        assert version != null : "version cannot be null";
        this.id = DefaultModuleIdentifier.newId(group, name);
        this.version = version;
        this.hashCode = 31 * id.hashCode() ^ version.hashCode();
    }

    private DefaultModuleVersionIdentifier(ModuleIdentifier id, String version) {
        assert version != null : "version cannot be null";
        this.id = id;
        this.version = version;
        // pre-compute the hashcode as it's going to be used anyway, and this object
        // is used as a key in several hash maps
        this.hashCode = 31 * id.hashCode() ^ version.hashCode();
    }

    @Override
    public String getGroup() {
        return id.getGroup();
    }

    @Override
    public String getName() {
        return id.getName();
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public String toString() {
        String group = id.getGroup();
        String module = id.getName();
        return group + ":" + module + ":" + version;
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
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public ModuleIdentifier getModule() {
        return id;
    }

    public static ModuleVersionIdentifier newId(Module module) {
        return new DefaultModuleVersionIdentifier(module.getGroup(), module.getName(), module.getVersion());
    }

    public static ModuleVersionIdentifier newId(ModuleIdentifier id, String version) {
        return new DefaultModuleVersionIdentifier(id, version);
    }

    public static ModuleVersionIdentifier newId(String group, String name, String version) {
        return new DefaultModuleVersionIdentifier(group, name, version);
    }

    public static ModuleVersionIdentifier newId(ModuleComponentIdentifier componentId) {
        return new DefaultModuleVersionIdentifier(componentId.getGroup(), componentId.getModule(), componentId.getVersion());
    }
}
