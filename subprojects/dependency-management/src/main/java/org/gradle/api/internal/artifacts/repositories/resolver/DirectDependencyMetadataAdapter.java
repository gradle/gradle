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

package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.model.IvyArtifactName;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

public class DirectDependencyMetadataAdapter extends AbstractDependencyMetadataAdapter<DirectDependencyMetadata> implements DirectDependencyMetadata {

    public DirectDependencyMetadataAdapter(ImmutableAttributesFactory attributesFactory, List<ModuleDependencyMetadata> container, int originalIndex) {
        super(attributesFactory, container, originalIndex);
    }

    @Override
    public void endorseStrictVersions() {
        updateMetadata(getOriginalMetadata().withEndorseStrictVersions(true));
    }

    @Override
    public void doNotEndorseStrictVersions() {
        updateMetadata(getOriginalMetadata().withEndorseStrictVersions(false));
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return getOriginalMetadata().isEndorsingStrictVersions();
    }

    @Nullable
    @Override
    public String getClassifier() {
        return getIvyArtifact().map(IvyArtifactName::getClassifier).orElse(null);
    }

    @Nullable
    @Override
    public String getType() {
        return getIvyArtifact().map(IvyArtifactName::getType).orElse(null);
    }

    private Optional<IvyArtifactName> getIvyArtifact() {
        ModuleDependencyMetadata originalMetadata = getOriginalMetadata();
        if (originalMetadata instanceof ConfigurationBoundExternalDependencyMetadata) {
            ConfigurationBoundExternalDependencyMetadata externalMetadata = (ConfigurationBoundExternalDependencyMetadata) originalMetadata;
            ExternalDependencyDescriptor descriptor = externalMetadata.getDependencyDescriptor();
            if (descriptor instanceof MavenDependencyDescriptor) {
                return Optional.ofNullable(((MavenDependencyDescriptor) descriptor).getDependencyArtifact());
            }
        }
        return Optional.empty();
    }

}
