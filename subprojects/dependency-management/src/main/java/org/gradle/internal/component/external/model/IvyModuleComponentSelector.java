/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ImmutableVersionConstraint;
import org.gradle.api.internal.artifacts.dependencies.DefaultImmutableVersionConstraint;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collection;
import java.util.List;

public class IvyModuleComponentSelector extends DefaultModuleComponentSelector implements ModuleComponentSelector {

    private final String branch;
    private final int hashCode;

    protected IvyModuleComponentSelector(ModuleIdentifier module, ImmutableVersionConstraint version, ImmutableAttributes attributes, ImmutableList<Capability> requestedCapabilities,
                                                 String branch) {
        super(module, version, attributes, requestedCapabilities);
        this.branch = branch;
        this.hashCode = 31 * super.hashCode() + branch.hashCode();
    }

    public String getBranch() {
        return branch;
    }

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder(super.getDisplayName().length() + branch.length() + 1);
        builder.append(super.getDisplayName());
        builder.append(":");
        builder.append(branch);
        return builder.toString();
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if (identifier instanceof IvyModuleComponentIdentifier) {
            return super.matchesStrictly(identifier) && ((IvyModuleComponentIdentifier)identifier).getBranch().equals(branch);
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

        IvyModuleComponentSelector that = (IvyModuleComponentSelector) o;

        if (hashCode != that.hashCode) {
            return false;
        }

        if (!super.equals(DefaultModuleComponentSelector.class.cast(o))) {
            return false;
        }

        return branch.equals(that.branch);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version, String branch) {
        return new IvyModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ImmutableAttributes.EMPTY, ImmutableList.of(), branch);
    }

    public static ModuleComponentSelector newSelector(ModuleIdentifier id, VersionConstraint version, AttributeContainer attributes, List<Capability> requestedCapabilities, String branch) {
        return new IvyModuleComponentSelector(id, DefaultImmutableVersionConstraint.of(version), ((AttributeContainerInternal) attributes).asImmutable(), ImmutableList.copyOf(requestedCapabilities), branch);
    }

}
