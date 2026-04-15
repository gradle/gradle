/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.resolver;

import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.configurations.ArtifactCollectionInternal;
import org.gradle.api.internal.artifacts.ivyservice.ResolutionFailureProvider;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creates {@link org.gradle.api.artifacts.ArtifactCollection} instances.
 */
public interface ArtifactCollectionFactory {

    /**
     * Create an artifact collection from the set of resolved artifacts it should include
     * and the set of failures it should report.
     */
    ArtifactCollectionInternal create(
        ResolvedArtifactSet artifacts,
        ResolutionFailureProvider failures,
        boolean lenient
    );

    record LazyResolvedArtifactSet(Supplier<ResolvedArtifactSet> delegate) implements ResolvedArtifactSet {

        @Override
        public void visit(Visitor visitor) {
            delegate.get().visit(visitor);
        }

        @Override
        public void visitTransformSources(TransformSourceVisitor visitor) {
            delegate.get().visitTransformSources(visitor);
        }

        @Override
        public void visitExternalArtifacts(Action<ResolvableArtifact> visitor) {
            delegate.get().visitExternalArtifacts(visitor);
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            delegate.get().visitDependencies(context);
        }

    }

    record LazyResolutionFailureProvider(Supplier<ResolutionFailureProvider> delegate) implements ResolutionFailureProvider {

        @Override
        public boolean hasAnyFailure() {
            return delegate.get().hasAnyFailure();
        }

        @Override
        public void visitFailures(Consumer<Throwable> visitor) {
            delegate.get().visitFailures(visitor);
        }

    }

}
