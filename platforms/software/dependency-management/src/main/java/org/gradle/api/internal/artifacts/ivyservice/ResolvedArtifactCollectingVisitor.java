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

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.component.Artifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.internal.DisplayName;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ResolvedArtifactCollectingVisitor implements ArtifactVisitor {
    private final Map<ArtifactIdentifier, DefaultResolvedArtifactResult> artifacts = new LinkedHashMap<>();
    private final Set<Throwable> failures = Sets.newLinkedHashSet();

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> capabilities, ResolvableArtifact artifact) {
        try {
            artifacts.compute(new ArtifactIdentifier(artifact.getId(), artifact.getFile()), (key, value) -> {
                if (value == null) {
                    return new DefaultResolvedArtifactResult(artifact.getId(), variantAttributes, capabilities, variantName, Artifact.class, artifact.getFile());
                } else {
                    return value.withAddedVariant(variantAttributes, capabilities, variantName);
                }
            });
        } catch (Exception t) {
            failures.add(t);
        }
    }

    @Override
    public boolean requireArtifactFiles() {
        return true;
    }

    public Set<ResolvedArtifactResult> getArtifacts() {
        return Sets.newLinkedHashSet(artifacts.values());
    }

    public Set<Throwable> getFailures() {
        return failures;
    }

    private static class ArtifactIdentifier {
        final ComponentArtifactIdentifier artifactIdentifier;
        final File artifactFile;

        public ArtifactIdentifier(ComponentArtifactIdentifier artifactIdentifier, File artifactFile) {
            this.artifactIdentifier = artifactIdentifier;
            this.artifactFile = artifactFile;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ArtifactIdentifier that = (ArtifactIdentifier) o;
            return Objects.equals(artifactIdentifier, that.artifactIdentifier) && Objects.equals(artifactFile, that.artifactFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactIdentifier, artifactFile);
        }
    }
}
