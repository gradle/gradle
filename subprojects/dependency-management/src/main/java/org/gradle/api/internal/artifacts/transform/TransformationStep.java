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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.project.ProjectStateRegistry;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.WorkNodeAction;
import org.gradle.internal.Try;
import org.gradle.internal.fingerprint.FileCollectionFingerprinterRegistry;
import org.gradle.internal.model.ModelContainer;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collections;

/**
 * A single transformation step.
 *
 * Transforms a subject by invoking a transformer on each of the subjects files.
 */
public class TransformationStep implements Transformation, TaskDependencyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformationStep.class);
    public static final Equivalence<? super TransformationStep> FOR_SCHEDULING = Equivalence.identity();

    private final Transformer transformer;
    private final TransformerInvocationFactory transformerInvocationFactory;
    private final ProjectStateRegistry.SafeExclusiveLock isolationLock;
    private final WorkNodeAction isolateAction;
    private final ProjectInternal owningProject;
    private final FileCollectionFingerprinterRegistry globalFingerprinterRegistry;
    private final ModelContainer owner;

    public TransformationStep(Transformer transformer, TransformerInvocationFactory transformerInvocationFactory, DomainObjectContext owner, ProjectStateRegistry projectRegistry, FileCollectionFingerprinterRegistry globalFingerprinterRegistry) {
        this.transformer = transformer;
        this.transformerInvocationFactory = transformerInvocationFactory;
        this.globalFingerprinterRegistry = globalFingerprinterRegistry;
        this.isolationLock = projectRegistry.newExclusiveOperationLock();
        this.owningProject = owner.getProject();
        this.owner = owner.getModel();
        this.isolateAction = transformer.isIsolated() ? null : new WorkNodeAction() {
            @Override
            public String toString() {
                return "isolate parameters of transform " + transformer.getDisplayName();
            }

            @Nullable
            @Override
            public Project getProject() {
                return owningProject;
            }

            @Override
            public void run(NodeExecutionContext context) {
                FileCollectionFingerprinterRegistry fingerprinterRegistry = context.getService(FileCollectionFingerprinterRegistry.class);
                isolateTransformerParameters(fingerprinterRegistry);
            }
        };
    }

    public Transformer getTransformer() {
        return transformer;
    }

    @Nullable
    public ProjectInternal getOwningProject() {
        return owningProject;
    }

    @Override
    public boolean endsWith(Transformation otherTransform) {
        return this == otherTransform;
    }

    @Override
    public int stepsCount() {
        return 1;
    }

    @Override
    public CacheableInvocation<TransformationSubject> createInvocation(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver, @Nullable NodeExecutionContext context, BuildOperationQueue<RunnableBuildOperation> workQueue) {
        return CacheableInvocation.nonCached(() -> {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transformer.getDisplayName());
            }

            FileCollectionFingerprinterRegistry fingerprinterRegistry = context != null ? context.getService(FileCollectionFingerprinterRegistry.class) : globalFingerprinterRegistry;
            isolateTransformerParameters(fingerprinterRegistry);
            Try<ArtifactTransformDependencies> resolvedDependencies = dependenciesResolver.forTransformer(transformer);
            if (!resolvedDependencies.isSuccessful()) {
                return Try.failure(resolvedDependencies.getFailure().get());
            }

            ImmutableList.Builder<File> builder = ImmutableList.builderWithExpectedSize(subjectToTransform.getArtifacts().size());
            for (ResolvableArtifact artifact : subjectToTransform.getArtifacts()) {
                File inputArtifact;
                try {
                    inputArtifact = artifact.getFile();
                } catch (ResolveException e) {
                    // TODO - collect all the failures
                    return Try.failure(e);
                } catch (RuntimeException e) {
                    return Try.failure(new DefaultLenientConfiguration.ArtifactResolveException("artifacts", artifact.getId().getDisplayName(), "artifact transform", Collections.singleton(e)));
                }

                Try<ImmutableList<File>> transformOutputs = transformerInvocationFactory.createInvocation(transformer, inputArtifact, resolvedDependencies.get(), subjectToTransform, fingerprinterRegistry).invoke();
                if (!transformOutputs.isSuccessful()) {
                    // TODO - collect all the failures
                    return Try.failure(transformOutputs.getFailure().get());
                }
                builder.addAll(transformOutputs.get());
            }
            return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()));
        });
    }

    private void isolateTransformerParameters(FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        if (!transformer.isIsolated()) {
            if (!owner.hasMutableState()) {
                owner.withLenientState(() -> isolateExclusively(fingerprinterRegistry));
            } else {
                isolateExclusively(fingerprinterRegistry);
            }
        }
    }

    private void isolateExclusively(FileCollectionFingerprinterRegistry fingerprinterRegistry) {
        isolationLock.withLock(() -> {
            if (!transformer.isIsolated()) {
                transformer.isolateParameters(fingerprinterRegistry);
            }
        });
    }

    @Override
    public boolean requiresDependencies() {
        return transformer.requiresDependencies();
    }

    @Override
    public String getDisplayName() {
        return transformer.getDisplayName();
    }

    @Override
    public void visitTransformationSteps(Action<? super TransformationStep> action) {
        action.execute(this);
    }

    public ImmutableAttributes getFromAttributes() {
        return transformer.getFromAttributes();
    }

    @Override
    public String toString() {
        return String.format("%s@%s", transformer.getDisplayName(), transformer.getSecondaryInputHash());
    }

    public TaskDependencyContainer getDependencies() {
        return transformer;
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        if (!transformer.isIsolated()) {
            context.add(isolateAction);
        }
        transformer.visitDependencies(context);
    }
}
