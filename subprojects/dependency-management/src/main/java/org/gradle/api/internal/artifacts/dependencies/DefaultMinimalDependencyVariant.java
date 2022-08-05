/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.internal.artifacts.dependencies;

import org.gradle.api.Action;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Actions;

import javax.annotation.Nullable;

public class DefaultMinimalDependencyVariant implements MinimalExternalModuleDependency, DependencyVariant {
    private final MinimalExternalModuleDependency delegate;
    private final Action<? super AttributeContainer> attributesMutator;
    private final Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator;
    private final String classifier;
    private final String artifactType;

    public DefaultMinimalDependencyVariant(MinimalExternalModuleDependency delegate,
                                           @Nullable Action<? super AttributeContainer> attributesMutator,
                                           @Nullable Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator,
                                           @Nullable String classifier,
                                           @Nullable String artifactType) {
        this.delegate = delegate;
        boolean delegateIsVariant = delegate instanceof DefaultMinimalDependencyVariant;
        this.attributesMutator = delegateIsVariant ? Actions.composite(((DefaultMinimalDependencyVariant) delegate).attributesMutator, attributesMutator == null ? Actions.doNothing() : attributesMutator) : attributesMutator;
        this.capabilitiesMutator = delegateIsVariant ? Actions.composite(((DefaultMinimalDependencyVariant) delegate).capabilitiesMutator, capabilitiesMutator == null ? Actions.doNothing() : capabilitiesMutator) : capabilitiesMutator;
        if (classifier == null && delegateIsVariant) {
            classifier = ((DefaultMinimalDependencyVariant) delegate).getClassifier();
        }
        if (artifactType == null && delegateIsVariant) {
            artifactType = ((DefaultMinimalDependencyVariant) delegate).getArtifactType();
        }
        this.classifier = classifier;
        this.artifactType = artifactType;
    }

    @Override
    public ModuleIdentifier getModule() {
        return delegate.getModule();
    }

    @Override
    public VersionConstraint getVersionConstraint() {
        return delegate.getVersionConstraint();
    }

    @Override
    public void mutateAttributes(AttributeContainer attributes) {
        if (attributesMutator!= null) {
            attributesMutator.execute(attributes);
        }
    }

    @Override
    public void mutateCapabilities(ModuleDependencyCapabilitiesHandler capabilitiesHandler) {
        if (capabilitiesMutator != null) {
            capabilitiesMutator.execute(capabilitiesHandler);
        }
    }

    @Nullable
    @Override
    public String getClassifier() {
        return classifier;
    }

    @Nullable
    @Override
    public String getArtifactType() {
        return artifactType;
    }

    @Override
    public String toString() {
        return delegate.toString();
    }
}
