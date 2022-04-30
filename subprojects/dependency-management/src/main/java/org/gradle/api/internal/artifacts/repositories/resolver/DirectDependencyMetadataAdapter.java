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

import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.external.descriptor.Artifact;
import org.gradle.internal.component.external.model.ConfigurationBoundExternalDependencyMetadata;
import org.gradle.internal.component.external.model.ExternalDependencyDescriptor;
import org.gradle.internal.component.external.model.GradleDependencyMetadata;
import org.gradle.internal.component.external.model.ModuleDependencyMetadata;
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor;
import org.gradle.internal.component.external.model.maven.MavenDependencyDescriptor;
import org.gradle.internal.component.model.IvyArtifactName;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DirectDependencyMetadataAdapter extends AbstractDependencyMetadataAdapter<DirectDependencyMetadata> implements DirectDependencyMetadata {

    public DirectDependencyMetadataAdapter(ImmutableAttributesFactory attributesFactory, ModuleDependencyMetadata metadata) {
        super(attributesFactory, metadata);
    }

    @Override
    public void endorseStrictVersions() {
        updateMetadata(getMetadata().withEndorseStrictVersions(true));
    }

    @Override
    public void doNotEndorseStrictVersions() {
        updateMetadata(getMetadata().withEndorseStrictVersions(false));
    }

    @Override
    public boolean isEndorsingStrictVersions() {
        return getMetadata().isEndorsingStrictVersions();
    }

    @Override
    public List<DependencyArtifact> getArtifactSelectors() {
        return getIvyArtifacts().stream().map(this::asDependencyArtifact).collect(Collectors.toList());
    }

    private DependencyArtifact asDependencyArtifact(IvyArtifactName ivyArtifactName) {
        return new DefaultDependencyArtifact(ivyArtifactName.getName(), ivyArtifactName.getType(), ivyArtifactName.getExtension(), ivyArtifactName.getClassifier(), null);
    }

    private List<IvyArtifactName> getIvyArtifacts() {
        ModuleDependencyMetadata originalMetadata = getMetadata();
        if (originalMetadata instanceof ConfigurationBoundExternalDependencyMetadata) {
            ConfigurationBoundExternalDependencyMetadata externalMetadata = (ConfigurationBoundExternalDependencyMetadata) originalMetadata;
            ExternalDependencyDescriptor descriptor = externalMetadata.getDependencyDescriptor();
            if (descriptor instanceof MavenDependencyDescriptor) {
                return fromMavenDescriptor((MavenDependencyDescriptor) descriptor);
            }
            if (descriptor instanceof IvyDependencyDescriptor) {
                return fromIvyDescriptor((IvyDependencyDescriptor) descriptor);
            }
        } else if (originalMetadata instanceof GradleDependencyMetadata){
            return fromGradleMetadata((GradleDependencyMetadata) originalMetadata);
        }
        return Collections.emptyList();
    }

    private List<IvyArtifactName> fromGradleMetadata(GradleDependencyMetadata metadata) {
        IvyArtifactName artifact = metadata.getDependencyArtifact();
        if(artifact != null) {
            return Collections.singletonList(artifact);
        }
        return Collections.emptyList();
    }

    private List<IvyArtifactName> fromIvyDescriptor(IvyDependencyDescriptor descriptor) {
        List<Artifact> artifacts = descriptor.getDependencyArtifacts();
        return artifacts.stream().map(Artifact::getArtifactName).collect(Collectors.toList());
    }

    private List<IvyArtifactName> fromMavenDescriptor(MavenDependencyDescriptor descriptor) {
        IvyArtifactName artifact = descriptor.getDependencyArtifact();
        if(artifact != null) {
            return Collections.singletonList(artifact);
        }
        return Collections.emptyList();
    }

}
