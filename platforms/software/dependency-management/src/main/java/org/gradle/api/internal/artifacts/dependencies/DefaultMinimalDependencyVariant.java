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
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.dsl.dependencies.ModuleFactoryHelper;
import org.gradle.internal.Actions;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;

public class DefaultMinimalDependencyVariant extends DefaultExternalModuleDependency implements MinimalExternalModuleDependencyInternal, DependencyVariant {
    private Action<? super AttributeContainer> attributesMutator;
    private Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator;
    private String classifier;
    private String artifactType;

    public DefaultMinimalDependencyVariant(MinimalExternalModuleDependency delegate,
                                           @Nullable Action<? super AttributeContainer> attributesMutator,
                                           @Nullable Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator,
                                           @Nullable String classifier,
                                           @Nullable String artifactType
    ) {
        super(delegate.getModule(), new DefaultMutableVersionConstraint(delegate.getVersionConstraint()), delegate.getTargetConfiguration());

        attributesMutator = GUtil.elvis(attributesMutator, Actions.doNothing());
        capabilitiesMutator = GUtil.elvis(capabilitiesMutator, Actions.doNothing());

        if (delegate instanceof DefaultMinimalDependencyVariant) {
            this.attributesMutator = Actions.composite(((DefaultMinimalDependencyVariant) delegate).attributesMutator, attributesMutator);
            this.capabilitiesMutator = Actions.composite(((DefaultMinimalDependencyVariant) delegate).capabilitiesMutator, capabilitiesMutator);
            this.classifier = GUtil.elvis(classifier, ((DefaultMinimalDependencyVariant) delegate).getClassifier());
            this.artifactType = GUtil.elvis(classifier, ((DefaultMinimalDependencyVariant) delegate).getArtifactType());
        } else {
            this.attributesMutator = attributesMutator;
            this.capabilitiesMutator = capabilitiesMutator;
            this.classifier = classifier;
            this.artifactType = artifactType;
        }

        MinimalExternalModuleDependencyInternal internal = (MinimalExternalModuleDependencyInternal) delegate;
        setAttributesFactory(internal.getAttributesFactory());
        setCapabilityNotationParser(internal.getCapabilityNotationParser());
    }

    private DefaultMinimalDependencyVariant(
        ModuleIdentifier id,
        MutableVersionConstraint versionConstraint,
        @Nullable String configuration,
        Action<? super AttributeContainer> attributesMutator,
        Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator,
        @Nullable String classifier,
        @Nullable String artifactType
    ) {
        super(id, versionConstraint, configuration);
        this.attributesMutator = attributesMutator;
        this.capabilitiesMutator = capabilitiesMutator;
        this.classifier = classifier;
        this.artifactType = artifactType;
    }

    @Override
    public void copyTo(AbstractExternalModuleDependency target) {
        super.copyTo(target);
        if (target instanceof DefaultMinimalDependencyVariant) {
            DefaultMinimalDependencyVariant depVariant = (DefaultMinimalDependencyVariant) target;
            depVariant.attributesMutator = attributesMutator;
            depVariant.capabilitiesMutator = capabilitiesMutator;
            depVariant.classifier = classifier;
            depVariant.artifactType = artifactType;
        } else {
            target.attributes(attributesMutator);
            target.capabilities(capabilitiesMutator);
            if (classifier != null || artifactType != null) {
                ModuleFactoryHelper.addExplicitArtifactsIfDefined(target, artifactType, classifier);
            }
        }
    }

    @Override
    public MinimalExternalModuleDependency copy() {
        DefaultMinimalDependencyVariant dependency = new DefaultMinimalDependencyVariant(
            getModule(), new DefaultMutableVersionConstraint(getVersionConstraint()), getTargetConfiguration(),
            attributesMutator, capabilitiesMutator, classifier, artifactType
        );
        copyTo(dependency);
        return dependency;
    }

    @Override
    public void because(String reason) {
        validateMutation();
    }

    @Override
    protected void validateMutation() {
        throw new UnsupportedOperationException("Minimal dependencies are immutable.");
    }

    @Override
    protected void validateMutation(Object currentValue, Object newValue) {
        validateMutation();
    }

    @Override
    public void mutateAttributes(AttributeContainer attributes) {
        attributesMutator.execute(attributes);
    }

    @Override
    public void mutateCapabilities(ModuleDependencyCapabilitiesHandler capabilitiesHandler) {
        capabilitiesMutator.execute(capabilitiesHandler);
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
        return "DefaultMinimalDependencyVariant{" +
            ", attributesMutator=" + attributesMutator +
            ", capabilitiesMutator=" + capabilitiesMutator +
            ", classifier='" + classifier + '\'' +
            ", artifactType='" + artifactType + '\'' +
            "} " + super.toString();
    }
}
