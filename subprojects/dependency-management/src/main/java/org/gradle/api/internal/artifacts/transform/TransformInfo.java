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
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.ResolvedArtifactCollectingVisitor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.BuildableSingleResolvedArtifactSet;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedArtifactSet;
import org.gradle.execution.taskgraph.TaskDependencyResolver;
import org.gradle.execution.taskgraph.WorkInfo;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class TransformInfo extends WorkInfo {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformInfo.class);
    private static final AtomicInteger ORDER_COUNTER = new AtomicInteger();

    private final int order = ORDER_COUNTER.incrementAndGet();
    protected final UserCodeBackedTransformer artifactTransformer;
    private List<File> result;
    private Throwable failure;

    public static TransformInfo from(
        List<UserCodeBackedTransformer> transformerChain,
        BuildableSingleResolvedArtifactSet artifact,
        BuildOperationExecutor buildOperationExecutor
    ) {
        Iterator<UserCodeBackedTransformer> iterator = transformerChain.iterator();
        TransformInfo current;
        TransformInfo previous = null;
        do {
            UserCodeBackedTransformer transformer = iterator.next();
            if (previous == null) {
                current = new InitialTransformInfo(transformer, artifact, buildOperationExecutor);
            } else {
                current = new ChainedTransformInfo(transformer, previous);
            }
            previous = current;
        } while (iterator.hasNext());
        return current;
    }

    protected TransformInfo(UserCodeBackedTransformer artifactTransformer) {
        this.artifactTransformer = artifactTransformer;
    }

    public void execute() {
        ResolvedTransformInputs inputs = resolveInputs();
        if (inputs.failure != null) {
            failure = inputs.failure;
            result = Collections.emptyList();
        } else {
            try {
                ImmutableList.Builder<File> builder = ImmutableList.builder();
                for (File inputFile : inputs.files) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("Executing transform {} on file {}", artifactTransformer.getDisplayName(), inputFile);
                    }
                    List<File> transformerResult = artifactTransformer.transform(inputFile);
                    builder.addAll(transformerResult);
                }
                result = builder.build();
            } catch (Exception e) {
                failure = e;
                result = Collections.emptyList();
            }
        }
    }

    protected abstract ResolvedTransformInputs resolveInputs();

    @Override
    public String toString() {
        return artifactTransformer.getDisplayName();
    }

    private class ResolvedTransformInputs {
        private final Collection<File> files;
        private final Throwable failure;

        public ResolvedTransformInputs(Collection<File> files) {
            this.files = files;
            this.failure = null;
        }

        public ResolvedTransformInputs(Throwable failure) {
            this.files = Collections.emptySet();
            this.failure = failure;
        }

        @Override
        public String toString() {
            return String.format("Input files: %s, failure: %s", files, failure);
        }
    }

    private List<File> getResult() {
        if (failure != null) {
            throw new IllegalStateException("Transformation has failed", failure);
        }
        if (result == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
        return result;
    }

    @Nullable
    private Throwable getFailure() {
        if (result == null) {
            throw new IllegalStateException("Transformation hasn't been executed yet");
        }
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
        return null;
    }

    @Override
    public void rethrowFailure() {
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
        protected ResolvedTransformInputs resolveInputs() {
            ResolveArtifacts resolveArtifacts = new ResolveArtifacts(artifactSet);
            buildOperationExecutor.runAll(resolveArtifacts);
            ResolvedArtifactCollectingVisitor visitor = new ResolvedArtifactCollectingVisitor();
            resolveArtifacts.getResult().visit(visitor);
            Set<Throwable> failures = visitor.getFailures();
            if (!failures.isEmpty()) {
                Throwable failure;
                if (failures.size() == 1 && Iterables.getOnlyElement(failures) instanceof ResolveException) {
                    failure = Iterables.getOnlyElement(failures);
                } else {
                    failure = new DefaultLenientConfiguration.ArtifactResolveException("artifacts", artifactTransformer.getDisplayName(), "artifact transform", failures);
                }
                return new ResolvedTransformInputs(failure);
            } else {
                ResolvedArtifactResult artifact = Iterables.getOnlyElement(visitor.getArtifacts());
                return new ResolvedTransformInputs(ImmutableList.of(artifact.getFile()));
            }
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
            Set<WorkInfo> dependencies = getDependencies(dependencyResolver);
            for (WorkInfo dependency : dependencies) {
                addDependencySuccessor(dependency);
                processHardSuccessor.execute(dependency);
            }
        }

        private Set<WorkInfo> getDependencies(TaskDependencyResolver dependencyResolver) {
            return dependencyResolver.resolveDependenciesFor(null, artifactSet.getBuildDependencies());
        }

        @Override
        public String toString() {
            return String.format("%s on %s", super.toString(), artifactSet.getArtifactId().getDisplayName());
        }
    }

    private static class ChainedTransformInfo extends TransformInfo {
        private final TransformInfo previousTransform;

        public ChainedTransformInfo(UserCodeBackedTransformer artifactTransformer, TransformInfo previousTransform) {
            super(artifactTransformer);
            this.previousTransform = previousTransform;
        }

        @Override
        protected ResolvedTransformInputs resolveInputs() {
            Throwable previousFailure = previousTransform.getFailure();
            if (previousFailure != null) {
                return new ResolvedTransformInputs(previousFailure);
            }
            List<File> inputFiles = previousTransform.getResult();
            return new ResolvedTransformInputs(inputFiles);
        }

        @Override
        public void resolveDependencies(TaskDependencyResolver dependencyResolver, Action<WorkInfo> processHardSuccessor) {
            addDependencySuccessor(previousTransform);
            processHardSuccessor.execute(previousTransform);
        }

        @Override
        public String toString() {
            return String.format("%s -> %s", super.toString(), previousTransform);
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
            return false;
        }

        @Override
        public void fileAvailable(File file) {
        }
    }
}
