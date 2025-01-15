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
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.internal.Actions;
import org.gradle.util.internal.GUtil;

import javax.annotation.Nullable;

public class DefaultMinimalDependencyVariant extends DefaultMinimalDependency implements MinimalExternalModuleDependencyInternal {
    private final Action<? super AttributeContainer> attributesMutator;
    private final Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator;
    private final String classifier;
    private final String artifactType;
    private final boolean endorseStrictVersions;

    public DefaultMinimalDependencyVariant(MinimalExternalModuleDependency delegate,
                                           @Nullable Action<? super AttributeContainer> attributesMutator,
                                           @Nullable Action<? super ModuleDependencyCapabilitiesHandler> capabilitiesMutator,
                                           @Nullable String classifier,
                                           @Nullable String artifactType,
                                           boolean endorseStrictVersions
    ) {
        super(delegate.getModule(), delegate.getVersionConstraint());
        attributesMutator = GUtil.elvis(attributesMutator, Actions.doNothing());
        capabilitiesMutator = GUtil.elvis(capabilitiesMutator, Actions.doNothing());

        if (delegate instanceof DefaultMinimalDependencyVariant) {
            this.attributesMutator = Actions.composite(((DefaultMinimalDependencyVariant) delegate).attributesMutator, attributesMutator);
            this.capabilitiesMutator = Actions.composite(((DefaultMinimalDependencyVariant) delegate).capabilitiesMutator, capabilitiesMutator);
            this.classifier = GUtil.elvis(classifier, ((DefaultMinimalDependencyVariant) delegate).getClassifier());
            this.artifactType = GUtil.elvis(classifier, ((DefaultMinimalDependencyVariant) delegate).getArtifactType());
            this.endorseStrictVersions = endorseStrictVersions || ((DefaultMinimalDependencyVariant) delegate).endorseStrictVersions;
        } else {
            this.attributesMutator = attributesMutator;
            this.capabilitiesMutator = capabilitiesMutator;
            this.classifier = classifier;
            this.artifactType = artifactType;
            this.endorseStrictVersions = endorseStrictVersions;
        }
    }

    public Action<? super AttributeContainer> getAttributesMutator() {
        return attributesMutator;
    }

    public Action<? super ModuleDependencyCapabilitiesHandler> getCapabilitiesMutator() {
        return capabilitiesMutator;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    @Nullable
    public String getArtifactType() {
        return artifactType;
    }

    public boolean isEndorseStrictVersions() {
        return endorseStrictVersions;
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
