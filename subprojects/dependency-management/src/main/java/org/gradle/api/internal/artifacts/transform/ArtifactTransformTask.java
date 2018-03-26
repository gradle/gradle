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

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ArtifactVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildDependenciesVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@NonNullApi
public class ArtifactTransformTask extends DefaultTask {

    private ArtifactTransformDependency artifactTransformDependency;

    public ArtifactTransformTask() {
        dependsOn(new TaskDependencyContainer() {
            @Override
            public void visitDependencies(final TaskDependencyResolveContext context) {
                artifactTransformDependency.getDelegate().collectBuildDependencies(new BuildDependenciesVisitor() {
                    @Override
                    public void visitDependency(Object dep) {
                        context.add(dep);
                    }

                    @Override
                    public void visitFailure(Throwable failure) {
                        throw new GradleException("Broken!");
                    }
                });
            }
        });
    }

    private ResolvedArtifactSet.Completion result;

    @Internal
    public ResolvedArtifactSet.Completion getResult() {
        return result;
    }

    @TaskAction
    public void transformArtifacts() {
        final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults = new ConcurrentHashMap<ResolvableArtifact, TransformArtifactOperation>();
        final Map<File, TransformFileOperation> fileResults = new ConcurrentHashMap<File, TransformFileOperation>();
        AsyncTransformAction asyncTransformAction = new AsyncTransformAction(artifactResults, fileResults);
        getBuildOperationExecuter().runAll(asyncTransformAction);
        result = new TransformingResult(asyncTransformAction.getResult(), artifactResults, fileResults, artifactTransformDependency.getAttributes());
    }

    public void setArtifactTransformDependency(ArtifactTransformDependency artifactTransformDependency) {
        this.artifactTransformDependency = artifactTransformDependency;
    }

    @Inject
    public BuildOperationExecutor getBuildOperationExecuter() {
        throw new UnsupportedOperationException();
    }

    private ResolvedArtifactSet getDelegate() {
        return artifactTransformDependency.getDelegate();
    }

    private ArtifactTransformer getTransform() {
        return artifactTransformDependency.getTransform();
    }

    public static class TransformingResult implements ResolvedArtifactSet.Completion {
        private final ResolvedArtifactSet.Completion result;
        private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
        private final Map<File, TransformFileOperation> fileResults;
        private final AttributeContainerInternal attributes;

        public TransformingResult(ResolvedArtifactSet.Completion result, Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults, AttributeContainerInternal attributes) {
            this.result = result;
            this.artifactResults = artifactResults;
            this.fileResults = fileResults;
            this.attributes = attributes;
        }

        @Override
        public void visit(ArtifactVisitor visitor) {
            result.visit(new ArtifactTransformingVisitor(visitor, attributes, artifactResults, fileResults));
        }
    }


    private class AsyncTransformAction implements Action<BuildOperationQueue<RunnableBuildOperation>> {
        private final Map<ResolvableArtifact, TransformArtifactOperation> artifactResults;
        private final Map<File, TransformFileOperation> fileResults;

        public ResolvedArtifactSet.Completion getResult() {
            return result;
        }

        private ResolvedArtifactSet.Completion result;

        public AsyncTransformAction(Map<ResolvableArtifact, TransformArtifactOperation> artifactResults, Map<File, TransformFileOperation> fileResults) {
            this.artifactResults = artifactResults;
            this.fileResults = fileResults;
        }

        @Override
        public void execute(BuildOperationQueue<RunnableBuildOperation> buildOperationQueue) {
            this.result = getDelegate().startVisit(buildOperationQueue, new TransformingAsyncArtifactListener(getTransform(), new ResolvedArtifactSet.AsyncArtifactListener() {
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
            }, buildOperationQueue, artifactResults, fileResults));
        }
    }
}
