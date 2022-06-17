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
package org.gradle.internal.component.model;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.deprecation.DeprecationMessageBuilder;

import java.util.List;
import java.util.Set;

public class DefaultSelectedByVariantMatchingConfigurationMetadata implements SelectedByVariantMatchingConfigurationMetadata {

    private final ConfigurationMetadata delegate;

    DefaultSelectedByVariantMatchingConfigurationMetadata(ConfigurationMetadata delegate) {
        this.delegate = delegate;
    }

    @Override
    public ImmutableSet<String> getHierarchy() {
        return delegate.getHierarchy();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public DisplayName asDescribable() {
        return delegate.asDescribable();
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public List<? extends DependencyMetadata> getDependencies() {
        return delegate.getDependencies();
    }

    @Override
    public ImmutableList<? extends ComponentArtifactMetadata> getArtifacts() {
        return delegate.getArtifacts();
    }

    @Override
    public Set<? extends VariantResolveMetadata> getVariants() {
        return delegate.getVariants();
    }

    @Override
    public ImmutableList<ExcludeMetadata> getExcludes() {
        return delegate.getExcludes();
    }

    @Override
    public boolean isTransitive() {
        return delegate.isTransitive();
    }

    @Override
    public boolean isVisible() {
        return delegate.isVisible();
    }

    @Override
    public boolean isCanBeConsumed() {
        return delegate.isCanBeConsumed();
    }

    @Override
    public DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation() {
        return delegate.getConsumptionDeprecation();
    }

    @Override
    public boolean isCanBeResolved() {
        return delegate.isCanBeResolved();
    }

    @Override
    public ComponentArtifactMetadata artifact(IvyArtifactName artifact) {
        return delegate.artifact(artifact);
    }

    @Override
    public CapabilitiesMetadata getCapabilities() {
        return delegate.getCapabilities();
    }

    @Override
    public boolean isExternalVariant() {
        return delegate.isExternalVariant();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultSelectedByVariantMatchingConfigurationMetadata that = (DefaultSelectedByVariantMatchingConfigurationMetadata) o;
        return Objects.equal(delegate, that.delegate);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(delegate);
    }
}
