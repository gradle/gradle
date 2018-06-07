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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.execution.taskgraph.TaskDependencyResolver;
import org.gradle.execution.taskgraph.WorkInfo;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import java.io.File;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransformInfo extends WorkInfo implements TransformOperation {
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    private final UserCodeBackedTransformer artifactTransformer;
    private List<File> result;
    protected Throwable failure;

    public static TransformInfo from(
        List<UserCodeBackedTransformer> transformerChain,
        BuildableSingleResolvedArtifactSet artifact,
        BuildOperationExecutor buildOperationExecutor
    ) {
        ListIterator<UserCodeBackedTransformer> iterator = transformerChain.listIterator(transformerChain.size());
        TransformInfo current;
        TransformInfo previous = null;
        do {
            UserCodeBackedTransformer transformer = iterator.previous();
            if (previous == null) {
                current = new InitialTransformInfo(transformer, artifact, buildOperationExecutor);
            } else {
                current = new ChainedTransformInfo(transformer, previous);
            }
            previous = current;
        } while (iterator.hasPrevious());
        return current;
    }

    protected TransformInfo(UserCodeBackedTransformer artifactTransformer) {
        this.artifactTransformer = artifactTransformer;
    }

    public void execute() {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (File inputFile : getInputFiles()) {
            try {
                List<File> transformerResult = artifactTransformer.transform(inputFile);
                builder.addAll(transformerResult);
            } catch (Exception e) {
                failure = e;
                break;
            }
        }
        result = builder.build();
    }

    protected abstract Iterable<File> getInputFiles();

    @Override
    public List<File> getResult() {
        if (failure != null) {
            throw new IllegalStateException("Transformation has failed", failure);
        }
        if (result == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return result;
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }

    @Override
    public void prepareForExecution() {
    }

    @Override
    public void collectTaskInto(ImmutableCollection.Builder<Task> builder) {
    }

    @Override
    public Throwable getWorkFailure() {
        return getFailure();
    }

    @Override
    public void rethrowFailure() {
        if (failure != null) {
            throw UncheckedException.throwAsUncheckedException(failure);
        }
    }

    @Override
    public int compareTo(WorkInfo other) {
        if (getClass() != other.getClass()) {
            return getClass().getName().compareTo(other.getClass().getName());
        }
        TransformInfo otherTransform = (TransformInfo) other;
        return order - otherTransform.order;
    }

    private static class InitialTransformInfo extends TransformInfo {
        private final BuildableSingleResolvedArtifactSet artifactSet;
        private final BuildOperationExecutor buildOperationExecutor;

        public InitialTransformInfo(
            UserCodeBackedTransformer artifactTransformer,
            BuildableSingleResolvedArtifactSet artifactSet,
            BuildOperationExecutor buildOperationExecutor
        ) {
            super(artifactTransformer);
            this.artifactSet = artifactSet;
            this.buildOperationExecutor = buildOperationExecutor;
        }

        @Override
        protected Iterable<File> getInputFiles() {
            ResolveArtifacts resolveArtifacts = new ResolveArtifacts(artifactSet);
            buildOperationExecutor.runAll(resolveArtifacts);
            ArtifactCollectingVisitor visitor = new ArtifactCollectingVisitor();
            resolveArtifacts.getResult().visit(visitor);
            ResolvedArtifact artifact = Iterables.getOnlyElement(visitor.getArtifacts());
            return ImmutableList.of(artifact.getFile());
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
            for (WorkInfo dependency : getDependencies(dependencyResolver)) {
                addDependencySuccessor(dependency);
            }
        }

        private Set<WorkInfo> getDependencies(TaskDependencyResolver dependencyResolver) {
            return dependencyResolver.resolveDependenciesFor(null, artifactSet.getBuildDependencies());
        }
    }

    private static class ChainedTransformInfo extends TransformInfo {
        private final TransformInfo previousTransform;

        public ChainedTransformInfo(UserCodeBackedTransformer artifactTransformer, TransformInfo previousTransform) {
            super(artifactTransformer);
            this.previousTransform = previousTransform;
        }

        @Override
        public void execute() {
            Throwable previousFailure = previousTransform.getFailure();
            if (previousFailure != null) {
                this.failure = previousFailure;
            } else {
                super.execute();
            }
        }

        @Override
        protected Iterable<File> getInputFiles() {
            List<File> inputFiles = previousTransform.getResult();
            assert inputFiles != null;
            return inputFiles;
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
            addDependencySuccessor(previousTransform);
        }
    }

    private static class ResolveArtifacts implements Action<BuildOperationQueue<RunnableBuildOperation>> {
        private final ResolvedArtifactSet artifactSet;
        private ResolvedArtifactSet.Completion result;

        public ResolveArtifacts(ResolvedArtifactSet artifactSet) {
            this.artifactSet = artifactSet;
        }

        @Override
        public void execute(BuildOperationQueue<RunnableBuildOperation> actions) {
            result = artifactSet.startVisit(actions, new ResolveOnlyAsyncArtifactListener());
        }

        public ResolvedArtifactSet.Completion getResult() {
            return result;
        }
    }

    private static class ResolveOnlyAsyncArtifactListener implements ResolvedArtifactSet.AsyncArtifactListener {
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
