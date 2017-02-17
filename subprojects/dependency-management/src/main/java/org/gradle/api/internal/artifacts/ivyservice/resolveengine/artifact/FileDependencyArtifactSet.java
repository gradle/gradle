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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;

import java.util.Collection;

public class FileDependencyArtifactSet implements ArtifactSet {
    private final long id;
    private final LocalFileDependencyMetadata fileDependency;
    private final ImmutableAttributesFactory attributesFactory;

    public FileDependencyArtifactSet(long id, LocalFileDependencyMetadata fileDependency, ImmutableAttributesFactory attributesFactory) {
        this.id = id;
        this.fileDependency = fileDependency;
        this.attributesFactory = attributesFactory;
    }

    @Override
    public long getId() {
        return id;
    }

    @Override
    public ArtifactSet snapshot() {
        return this;
    }

    @Override
    public ResolvedArtifactSet select(Spec<? super ComponentIdentifier> componentFilter, Transformer<ResolvedArtifactSet, Collection<? extends ResolvedVariant>> selector) {
        return new LocalFileDependencyBackedArtifactSet(fileDependency, componentFilter, selector, attributesFactory);
    }

}
