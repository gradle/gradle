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
package org.gradle.api.internal.artifacts.component;

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;

public class DefaultModuleComponentIdentifier implements ModuleComponentIdentifier {
    private final String displayName;
    private final String group;
    private final String module;
    private final String version;

    public DefaultModuleComponentIdentifier(String group, String module, String version) {
        assert group != null : "group cannot be null";
        assert module != null : "module cannot be null";
        assert version != null : "version cannot be null";
        displayName = String.format("%s:%s:%s", group, module, version);
        this.group = group;
        this.module = module;
        this.version = version;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroup() {
        return group;
    }

    public String getModule() {
        return module;
    }

    public String getVersion() {
        return version;
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

        if (!group.equals(that.group)) {
            return false;
        }
        if (!module.equals(that.module)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + module.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ModuleComponentIdentifier newId(String group, String name, String version) {
        return new DefaultModuleComponentIdentifier(group, name, version);
    }

    public static ModuleComponentIdentifier newId(ModuleVersionIdentifier moduleVersionIdentifier) {
        return new DefaultModuleComponentIdentifier(moduleVersionIdentifier.getGroup(), moduleVersionIdentifier.getName(), moduleVersionIdentifier.getVersion());
    }
}

