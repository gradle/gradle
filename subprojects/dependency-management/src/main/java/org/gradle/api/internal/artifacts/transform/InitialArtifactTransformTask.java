/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactBackedResolvedVariant;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.internal.work.WorkerLeaseService;

import javax.inject.Inject;
import java.io.File;

@NonNullApi
public class InitialArtifactTransformTask extends ArtifactTransformTask {
    private final ArtifactBackedResolvedVariant.SingleArtifactSet delegate;

    @Inject
    public InitialArtifactTransformTask(UserCodeBackedTransformer transform, ArtifactBackedResolvedVariant.SingleArtifactSet delegate, WorkerLeaseService workerLeaseService) {
        super(transform, workerLeaseService);
        this.delegate = delegate;
        dependsOn(new TaskDependencyContainer() {
            @Override
            public void visitDependencies(final TaskDependencyResolveContext context) {
                InitialArtifactTransformTask.this.delegate.collectBuildDependencies(new BuildDependenciesVisitor() {
                    @Override
                    public void visitDependency(Object dep) {
                        context.add(dep);
                    }

                    @Override
                    public void visitFailure(Throwable failure) {
                        throw new GradleException(String.format("Could not determine dependencies of %s.", InitialArtifactTransformTask.this.delegate.getArtifact().getId().getDisplayName()), failure);
                    }
                });
            }
        });
    }

    private ResolvedArtifactSet.Completion resolveArtifacts() {
        ResolveArtifacts resolveArtifacts = new ResolveArtifacts(delegate);
        getBuildOperationExecuter().runAll(resolveArtifacts);
        return resolveArtifacts.getResult();
    }

    @Override
    public ArtifactTransformationResult incomingTransformationResult() {
        ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
        resolveArtifacts().visit(visitor);
        ResolvedArtifact artifact = Iterables.getOnlyElement(visitor.getArtifacts());
        return new TransformationResult(ImmutableList.of(artifact.getFile()));
    }

    private static class ResolveArtifacts implements Action<BuildOperationQueue<RunnableBuildOperation>> {

        private final ResolvedArtifactSet delegate;
        private ResolvedArtifactSet.Completion result;

        public ResolveArtifacts(ResolvedArtifactSet delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(BuildOperationQueue<RunnableBuildOperation> actions) {
            result = delegate.startVisit(actions, new NoOpAsyncArtifactListener());
        }

        public ResolvedArtifactSet.Completion getResult() {
            return result;
        }
    }

    private static class NoOpAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
        @Override
        public void artifactAvailable(ResolvableArtifact artifact) {
        }

        @Override
        public boolean requireArtifactFiles() {
            return true;
        }

        @Override
        public boolean includeFileDependencies() {
            return true;
        }

        @Override
        public void fileAvailable(File file) {
        }
    }
}
