/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.LocalDependencyFiles;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ArtifactCollectingVisitor implements ArtifactVisitor {
    private final Set<ResolvedArtifact> artifacts;
    private List<Throwable> failures;

    public ArtifactCollectingVisitor() {
        this(new LinkedHashSet<>());
    }

    public ArtifactCollectingVisitor(Set<ResolvedArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
        this.artifacts.add(artifact.toPublicView());
    }

    @Override
    public void visitFailure(Throwable failure) {
        if (failures == null) {
            failures = new ArrayList<>();
        }
        failures.add(failure);
    }

    @Override
    public FileCollectionStructureVisitor.VisitType prepareForVisit(FileCollectionInternal.Source source) {
        if (source instanceof LocalDependencyFiles) {
            return FileCollectionStructureVisitor.VisitType.NoContents;
        }
        return FileCollectionStructureVisitor.VisitType.Visit;
    }

    @Override
    public boolean requireArtifactFiles() {
        return false;
    }

    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }

    public List<Throwable> getFailures() {
        return failures != null ? failures : Collections.emptyList();
    }
}
