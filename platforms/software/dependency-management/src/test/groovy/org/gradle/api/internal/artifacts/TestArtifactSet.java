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

package org.gradle.api.internal.artifacts;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.Collection;

public class TestArtifactSet implements ResolvedArtifactSet, ResolvedArtifactSet.Artifacts {
    public static final String DEFAULT_TEST_VARIANT = "test variant";
    private final DisplayName variantName;
    private final AttributeContainer variant;
    private final ImmutableSet<ResolvedArtifact> artifacts;

    private TestArtifactSet(String variantName, AttributeContainer variant, Collection<? extends ResolvedArtifact> artifacts) {
        this.variantName = Describables.of(variantName);
        this.variant = variant;
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public static ResolvedArtifactSet create(String variantName, AttributeContainer variantAttributes, Collection<? extends ResolvedArtifact> artifacts) {
        return new TestArtifactSet(variantName, variantAttributes, artifacts);
    }

    public static ResolvedArtifactSet create(AttributeContainer variantAttributes, Collection<? extends ResolvedArtifact> artifacts) {
        return new TestArtifactSet(DEFAULT_TEST_VARIANT, variantAttributes, artifacts);
    }

    @Override
    public void visit(Visitor visitor) {
        visitor.visitArtifacts(this);
    }

    @Override
    public void startFinalization(BuildOperationQueue<RunnableBuildOperation> actions, boolean requireFiles) {
    }

    @Override
    public void visit(ArtifactVisitor visitor) {
        for (final ResolvedArtifact artifact : artifacts) {
            visitor.visitArtifact(variantName, variant, ImmutableCapabilities.EMPTY, new Adapter(artifact));
        }
    }

    @Override
    public void visitTransformSources(TransformSourceVisitor visitor) {
    }

    @Override
    public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        for (ResolvedArtifact artifact : artifacts) {
            context.add(artifact);
        }
    }

    private static class Adapter implements ResolvableArtifact {
        private final ResolvedArtifact artifact;

        Adapter(ResolvedArtifact artifact) {
            this.artifact = artifact;
        }

        @Override
        public CalculatedValue<File> getFileSource() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
        }

        @Override
        public ComponentArtifactIdentifier getId() {
            return artifact.getId();
        }

        @Override
        public boolean isResolveSynchronously() {
            return false;
        }

        @Override
        public File getFile() {
            return artifact.getFile();
        }

        @Override
        public IvyArtifactName getArtifactName() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvableArtifact transformedTo(File file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResolvedArtifact toPublicView() {
            return artifact;
        }
    }
}
