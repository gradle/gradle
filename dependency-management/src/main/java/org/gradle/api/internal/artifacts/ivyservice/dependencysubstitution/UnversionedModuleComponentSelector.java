/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import com.google.common.base.Objects;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collections;
import java.util.List;

class UnversionedModuleComponentSelector implements ComponentSelector {
    private final ModuleIdentifier moduleIdentifier;

    UnversionedModuleComponentSelector(ModuleIdentifier id) {
        this.moduleIdentifier = id;
    }

    public ModuleIdentifier getModuleIdentifier() {
        return moduleIdentifier;
    }

    @Override
    public String getDisplayName() {
        return moduleIdentifier.toString() + ":*";
    }

    @Override
    public boolean matchesStrictly(ComponentIdentifier identifier) {
        return false;
    }

    @Override
    public AttributeContainer getAttributes() {
        return ImmutableAttributes.EMPTY;
    }

    @Override
    public List<Capability> getRequestedCapabilities() {
        return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UnversionedModuleComponentSelector that = (UnversionedModuleComponentSelector) o;
        return Objects.equal(moduleIdentifier, that.moduleIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(moduleIdentifier);
    }
}
