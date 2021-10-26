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
import org.gradle.util.internal.CollectionUtils;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ResolvedArtifactCollectingVisitor implements ArtifactVisitor {
    private final Set<ResolvedArtifactResult> artifacts = Sets.newLinkedHashSet();
    private final Set<Throwable> failures = Sets.newLinkedHashSet();
    private final Set<Identifier> seenArtifacts = new HashSet<>();

    @Override
    public void visitFailure(Throwable failure) {
        failures.add(failure);
    }

    @Override
    public void visitArtifact(DisplayName variantName, AttributeContainer variantAttributes, List<? extends Capability> capabilities, ResolvableArtifact artifact) {
        try {
            Identifier id = new Identifier(artifact.getId(), variantAttributes, capabilities);
            if (seenArtifacts.add(id)) {
                File file = artifact.getFile();
                this.artifacts.add(new DefaultResolvedArtifactResult(artifact.getId(), variantAttributes, capabilities, variantName, Artifact.class, file));
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

    /**
     * A data class to serve as a key for storing artifact and variant identities
     */
    private static class Identifier {
        private final ComponentArtifactIdentifier artifactIdentifier;
        private final AttributeContainer variantAttributes;
        private final Set<? extends Capability> variantCapabilities;

        Identifier(ComponentArtifactIdentifier artifactIdentifier, AttributeContainer variantAttributes, List<? extends Capability> variantCapabilities) {
            this.artifactIdentifier = artifactIdentifier;
            this.variantAttributes = variantAttributes;
            this.variantCapabilities = CollectionUtils.toSet(variantCapabilities);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Identifier) {
                Identifier other = (Identifier) obj;
                return this.artifactIdentifier.equals(other.artifactIdentifier) &&
                    this.variantAttributes.equals(other.variantAttributes) &&
                    this.variantCapabilities.size() == other.variantCapabilities.size() &&
                    this.variantCapabilities.containsAll(other.variantCapabilities);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(artifactIdentifier, variantAttributes, variantCapabilities);
        }
    }
}
