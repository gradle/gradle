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

package org.gradle.api.internal.artifacts.result;

import com.google.common.collect.ImmutableList;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class DefaultResolvedVariantResult implements ResolvedVariantResult {

    private final ComponentIdentifier owner;
    private final DisplayName displayName;
    private final AttributeContainer attributes;
    private final ImmutableCapabilities capabilities;
    private final ResolvedVariantResult externalVariant;
    private final int hashCode;

    public DefaultResolvedVariantResult(ComponentIdentifier owner,
                                        DisplayName displayName,
                                        AttributeContainer attributes,
                                        ImmutableCapabilities capabilities,
                                        @Nullable ResolvedVariantResult externalVariant) {
        this.owner = owner;
        this.displayName = displayName;
        this.attributes = attributes;
        this.capabilities = capabilities;
        this.externalVariant = externalVariant;
        this.hashCode = computeHashCode();
    }

    @Override
    public ComponentIdentifier getOwner() {
        return owner;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public String getDisplayName() {
        return displayName.getDisplayName();
    }

    @Override
    public List<Capability> getCapabilities() {
        return ImmutableList.copyOf(capabilities.asSet());
    }

    @Override
    public Optional<ResolvedVariantResult> getExternalVariant() {
        return Optional.ofNullable(externalVariant);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultResolvedVariantResult that = (DefaultResolvedVariantResult) o;

        if (!owner.equals(that.owner)) {
            return false;
        }
        if (!displayName.equals(that.displayName)) {
            return false;
        }
        if (!attributes.equals(that.attributes)) {
            return false;
        }
        if (!capabilities.equals(that.capabilities)) {
            return false;
        }
        return externalVariant == null ? that.externalVariant == null : externalVariant.equals(that.externalVariant);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    private int computeHashCode() {
        int result = owner.hashCode();
        result = 31 * result + displayName.hashCode();
        result = 31 * result + attributes.hashCode();
        result = 31 * result + capabilities.hashCode();
        if (externalVariant != null) {
            result = 31 * externalVariant.hashCode();
        }
        return result;
    }

    @Override
    public String toString() {
        return displayName.toString();
    }
}
