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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.attributes.AttributeDesugaring;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.io.File;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public class ResolvedArtifactCollectingVisitor implements ArtifactVisitor {
    private final Set<ResolvedArtifactResult> artifacts = new LinkedHashSet<>();
    private final Set<Throwable> failures = new LinkedHashSet<>();
    private final Set<ComponentArtifactIdentifier> seenArtifacts = new HashSet<>();
    private final AttributeDesugaring attributeDesugaring;

    public ResolvedArtifactCollectingVisitor(AttributeDesugaring attributeDesugaring) {
        this.attributeDesugaring = attributeDesugaring;
    }

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public void visitArtifact(DisplayName variantName, ImmutableAttributes variantAttributes, ImmutableCapabilities capabilities, ResolvableArtifact artifact) {
        try {
            if (seenArtifacts.add(artifact.getId())) {
                File file = artifact.getFile();
                this.artifacts.add(new DefaultResolvedArtifactResult(artifact.getId(), attributeDesugaring.desugar(variantAttributes), capabilities, variantName, Artifact.class, file));
            }
        } catch (Exception t) {
            failures.add(t);
        }
    }

    @Override
    public boolean requireArtifactFiles() {
        return true;
    }

    public Set<ResolvedArtifactResult> getArtifacts() {
        return artifacts;
    }

    public Set<Throwable> getFailures() {
        return failures;
    }
}
