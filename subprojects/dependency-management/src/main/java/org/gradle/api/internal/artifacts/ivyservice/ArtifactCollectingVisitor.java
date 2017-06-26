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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.util.Set;

public class ArtifactCollectingVisitor implements ArtifactVisitor {
    private final Set<ResolvedArtifact> artifacts;

    public ArtifactCollectingVisitor() {
        this(Sets.<ResolvedArtifact>newLinkedHashSet());
    }

    public ArtifactCollectingVisitor(Set<ResolvedArtifact> artifacts) {
        this.artifacts = artifacts;
    }

    @Override
    public void visitArtifact(AttributeContainer variant, ResolvableArtifact artifact) {
        this.artifacts.add(artifact.toPublicView());
    }

    @Override
    public void visitFailure(Throwable failure) {
        throw UncheckedException.throwAsUncheckedException(failure);
    }

    @Override
    public boolean includeFiles() {
        return false;
    }

    @Override
    public boolean requireArtifactFiles() {
        return false;
    }

    @Override
    public void visitFile(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variant, File file) {
        throw new UnsupportedOperationException();
    }

    public Set<ResolvedArtifact> getArtifacts() {
        return artifacts;
    }
}
