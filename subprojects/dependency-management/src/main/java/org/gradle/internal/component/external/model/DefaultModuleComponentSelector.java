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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

public class DefaultModuleComponentSelector implements ModuleComponentSelector {
    private final ModuleIdentifier moduleIdentifier;
    private final ImmutableVersionConstraint versionConstraint;
    private final ImmutableAttributes attributes;
    private final int hashCode;

    private DefaultModuleComponentSelector(ModuleIdentifier module, ImmutableVersionConstraint version, ImmutableAttributes attributes) {
        assert module != null : "module cannot be null";
        assert version != null : "version cannot be null";
        assert attributes != null : "attributes cannot be null";
        this.moduleIdentifier = module;
        this.versionConstraint = version;
        this.attributes = attributes;
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        this.hashCode = Objects.hashCode(version, module, attributes);
    }

    public String getDisplayName() {
        String group = moduleIdentifier.getGroup();
        String module = moduleIdentifier.getName();
        StringBuilder builder = new StringBuilder(group.length() + module.length() + versionConstraint.getRequiredVersion().length() + 2);
        builder.append(group);
        builder.append(":");
        builder.append(module);
        String versionString = versionConstraint.getDisplayName();
        if (versionString.length() > 0) {
            builder.append(":");
            builder.append(versionString);
        }
        return builder.toString();
    }

    public String getGroup() {
        return moduleIdentifier.getGroup();
    }

    public String getModule() {
        return moduleIdentifier.getName();
    }

    public String getVersion() {
        return versionConstraint.getRequiredVersion().isEmpty()
            ? versionConstraint.getPreferredVersion()
            : versionConstraint.getRequiredVersion();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return versionConstraint;
    }

    @Override
    public ModuleIdentifier getModuleIdentifier() {
        return moduleIdentifier;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof ModuleComponentIdentifier) {
            ModuleComponentIdentifier moduleComponentIdentifier = (ModuleComponentIdentifier) identifier;
            return moduleIdentifier.getName().equals(moduleComponentIdentifier.getModule())
                && moduleIdentifier.getGroup().equals(moduleComponentIdentifier.getGroup())
                && getVersion().equals(moduleComponentIdentifier.getVersion());
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

        if (!moduleIdentifier.equals(that.moduleIdentifier)) {
            return false;
        }
        if (!versionConstraint.equals(that.versionConstraint)) {
            return false;
        }
        if (!attributes.equals(that.attributes)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version, AttributeContainer attributes) {
        assert attributes != null : "attributes cannot be null";
        assert version != null : "version cannot be null";
        assertModuleIdentifier(id);
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ((AttributeContainerInternal)attributes).asImmutable());
    }

    private static void assertModuleIdentifier(ModuleIdentifier id) {
        assert id.getGroup() != null : "group cannot be null";
        assert id.getName() != null : "name cannot be null";
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version) {
        assert version != null : "version cannot be null";
        assertModuleIdentifier(id);
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY);
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, String version) {
        assertModuleIdentifier(id);
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY);
    }

    public static ModuleComponentSelector newSelector(ModuleVersionSelector selector) {
        return new DefaultModuleComponentSelector(selector.getModule(), DefaultImmutableVersionConstraint.of(selector.getVersion()), ImmutableAttributes.EMPTY);
    }
}
