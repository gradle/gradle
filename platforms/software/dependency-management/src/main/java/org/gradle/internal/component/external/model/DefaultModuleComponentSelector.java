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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.internal.artifacts.capability.SpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.capability.FeatureCapabilitySelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.capability.DefaultSpecificCapabilitySelector;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.List;
import java.util.Set;

public class DefaultModuleComponentSelector implements ModuleComponentSelector {
    private final ModuleIdentifier moduleIdentifier;
    private final ImmutableVersionConstraint versionConstraint;
    private final ImmutableAttributes attributes;
    private final ImmutableSet<CapabilitySelector> capabilitySelectors;
    private final int hashCode;

    private DefaultModuleComponentSelector(ModuleIdentifier module, ImmutableVersionConstraint version, ImmutableAttributes attributes, ImmutableSet<CapabilitySelector> capabilitySelectors) {
        this.moduleIdentifier = module;
        this.versionConstraint = version;
        this.attributes = attributes;
        this.capabilitySelectors = capabilitySelectors;
        // Do NOT change the order of members used in hash code here, it's been empirically
        // tested to reduce the number of collisions on a large dependency graph (performance test)
        this.hashCode = computeHashcode(module, version, attributes, capabilitySelectors);
    }

    private int computeHashcode(ModuleIdentifier module, ImmutableVersionConstraint version, ImmutableAttributes attributes, ImmutableSet<CapabilitySelector> capabilitySelectors) {
        int hashCode = version.hashCode();
        hashCode = 31 * hashCode + module.hashCode();
        hashCode = 31 * hashCode + attributes.hashCode();
        hashCode = 31 * hashCode + capabilitySelectors.hashCode();
        return hashCode;
    }

    @Override
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
        return versionConstraint.getRequiredVersion();
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
    @SuppressWarnings("deprecation")
    public List<Capability> getRequestedCapabilities() {
        return capabilitySelectors.stream()
            .map(c -> {
                if (c instanceof SpecificCapabilitySelector) {
                    return ((DefaultSpecificCapabilitySelector) c).getBackingCapability();
                } else if (c instanceof FeatureCapabilitySelector) {
                    return new DefaultImmutableCapability(
                        getGroup(),
                        getModule() + "-" + ((FeatureCapabilitySelector) c).getFeatureName(),
                        getVersion()
                    );
                } else {
                    throw new UnsupportedOperationException("Unsupported capability selector type: " + c.getClass().getName());
                }
            })
            .collect(ImmutableList.toImmutableList());
    }

    @Override
    public ImmutableSet<CapabilitySelector> getCapabilitySelectors() {
        return capabilitySelectors;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
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

        if (hashCode != that.hashCode) {
            return false;
        }

        if (!moduleIdentifier.equals(that.moduleIdentifier)) {
            return false;
        }
        if (!versionConstraint.equals(that.versionConstraint)) {
            return false;
        }
        if (!attributes.equals(that.attributes)) {
            return false;
        }
        return capabilitySelectors.equals(that.capabilitySelectors);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version, AttributeContainer attributes, Set<CapabilitySelector> capabilitySelectors) {
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ((AttributeContainerInternal) attributes).asImmutable(), ImmutableSet.copyOf(capabilitySelectors));
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version) {
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY, ImmutableSet.of());
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, String version) {
        return new DefaultModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY, ImmutableSet.of());
    }

    public static ModuleComponentSelector newSelector(ModuleVersionSelector selector) {
        return new DefaultModuleComponentSelector(selector.getModule(), DefaultImmutableVersionConstraint.of(selector.getVersion()), ImmutableAttributes.EMPTY, ImmutableSet.of());
    }

    public static ModuleComponentSelector withAttributes(ModuleComponentSelector selector, ImmutableAttributes attributes) {
        DefaultModuleComponentSelector cs = (DefaultModuleComponentSelector) selector;
        return new DefaultModuleComponentSelector(
            cs.moduleIdentifier,
            cs.versionConstraint,
            attributes,
            cs.capabilitySelectors
        );
    }

    public static ComponentSelector withCapabilities(ModuleComponentSelector selector, ImmutableSet<CapabilitySelector> capabilitySelectors) {
        DefaultModuleComponentSelector cs = (DefaultModuleComponentSelector) selector;
        return new DefaultModuleComponentSelector(
            cs.moduleIdentifier,
            cs.versionConstraint,
            cs.attributes,
            ImmutableSet.copyOf(capabilitySelectors)
        );
    }
}
