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

import com.google.common.base.Objects;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;

public class DefaultModuleComponentSelector implements ModuleComponentSelector {
    private static final Interner<DefaultModuleComponentSelector> INSTANCES_INTERNER = Interners.newStrongInterner();
    private final DefaultModuleIdentifier id;
    private final String version;
    private String displayName;
    private final int hashCode;

    private DefaultModuleComponentSelector(String group, String module, String version) {
        assert group != null : "group cannot be null";
        assert module != null : "module cannot be null";
        assert version != null : "version cannot be null";
        this.id = DefaultModuleIdentifier.of(group, module);
        this.version = version;
        this.hashCode = calculateHashCode();
    }

    public static DefaultModuleComponentSelector of(String group, String module, String version) {
        DefaultModuleComponentSelector instance = new DefaultModuleComponentSelector(group, module, version);
        return INSTANCES_INTERNER.intern(instance);
    }

    private String createDisplayName() {
        StringBuilder builder = new StringBuilder(id.getGroup().length() + id.getName().length() + version.length() + 2);
        builder.append(id.getGroup());
        builder.append(":");
        builder.append(id.getName());
        builder.append(":");
        builder.append(version);
        return builder.toString();
    }

    public String getDisplayName() {
        if (displayName == null) {
            displayName = createDisplayName();
        }
        return displayName;
    }

    public String getGroup() {
        return id.getGroup();
    }

    public String getModule() {
        return id.getName();
    }

    public String getVersion() {
        return version;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if(identifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier)identifier;
            return id.getName().equals(moduleComponentIdentifier.getModule())
                    && id.getGroup().equals(moduleComponentIdentifier.getGroup())
                    && version.equals(moduleComponentIdentifier.getVersion());
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultModuleComponentSelector that = (DefaultModuleComponentSelector) o;

        if (hashCode() != that.hashCode()) {
            return false;
        }

        if (!id.equals(that.id)) {
            return false;
        }
        if (!version.equals(that.version)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int calculateHashCode() {
        return Objects.hashCode(id, version);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentSelector newSelector(String group, String name, String version) {
        return of(group, name, version);
    }

    public static ModuleComponentSelector newSelector(ModuleVersionSelector selector) {
        return of(selector.getGroup(), selector.getName(), selector.getVersion());
    }
}
