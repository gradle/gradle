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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Describable;
import org.gradle.api.internal.DomainObjectContext;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.internal.Cast;
import org.gradle.internal.Deferrable;
import org.gradle.internal.Try;
import org.gradle.internal.execution.InputFingerprinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;

/**
 * A single transform step in a transform chain.
 * <p>
 * Transforms a subject by invoking a transform on each of the subjects files.
 *
 * @see TransformChain
 */
public class TransformStep implements TaskDependencyContainer, Describable {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformStep.class);

    private final Transform transform;
    private final TransformInvocationFactory transformInvocationFactory;
    private final ProjectInternal owningProject;
    private final InputFingerprinter globalInputFingerprinter;

    public TransformStep(Transform transform, TransformInvocationFactory transformInvocationFactory, DomainObjectContext owner, InputFingerprinter globalInputFingerprinter) {
        this.transform = transform;
        this.transformInvocationFactory = transformInvocationFactory;
        this.globalInputFingerprinter = globalInputFingerprinter;
        this.owningProject = owner.getProject();
    }

    public Transform getTransform() {
        return transform;
    }

    @Nullable
    public ProjectInternal getOwningProject() {
        return owningProject;
    }

    public Deferrable<Try<TransformStepSubject>> createInvocation(TransformStepSubject subjectToTransform, TransformUpstreamDependencies upstreamDependencies, @Nullable NodeExecutionContext context) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transform.getDisplayName());
        }

        InputFingerprinter inputFingerprinter = context != null ? context.getService(InputFingerprinter.class) : globalInputFingerprinter;

        Try<TransformDependencies> resolvedDependencies = upstreamDependencies.computeArtifacts();
        return resolvedDependencies
            .map(dependencies -> {
                ImmutableList<File> inputArtifacts = subjectToTransform.getFiles();
                if (inputArtifacts.isEmpty()) {
                    return Deferrable.completed(Try.successful(subjectToTransform.createSubjectFromResult(ImmutableList.of())));
                } else if (inputArtifacts.size() > 1) {
                    return Deferrable.deferred(() ->
                        doTransform(subjectToTransform, inputFingerprinter, dependencies, inputArtifacts)
                    );
                } else {
                    File inputArtifact = inputArtifacts.get(0);
                    return transformInvocationFactory.createInvocation(transform, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                        .map(result -> result.map(subjectToTransform::createSubjectFromResult));
                }
            })
            .getOrMapFailure(failure -> Deferrable.completed(Try.failure(failure)));
    }

    private Try<TransformStepSubject> doTransform(TransformStepSubject subjectToTransform, InputFingerprinter inputFingerprinter, TransformDependencies dependencies, ImmutableList<File> inputArtifacts) {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (File inputArtifact : inputArtifacts) {
            Try<ImmutableList<File>> result = transformInvocationFactory
                .createInvocation(transform, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                .completeAndGet();

            if (result.getFailure().isPresent()) {
                return Cast.uncheckedCast(result);
            }
            builder.addAll(result.get());
        }
        return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()));
    }

    public void isolateParametersIfNotAlready() {
        transform.isolateParametersIfNotAlready();
    }

    public boolean requiresDependencies() {
        return transform.requiresDependencies();
    }

    @Override
    public String getDisplayName() {
        return transform.getDisplayName();
    }

    public ImmutableAttributes getFromAttributes() {
        return transform.getFromAttributes();
    }

    @Override
    public String toString() {
        return transform.getDisplayName();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        transform.visitDependencies(context);
    }
}
