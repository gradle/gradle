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
import org.gradle.api.Action;
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
 * A single transformation step.
 *
 * Transforms a subject by invoking a transformer on each of the subjects files.
 */
public class TransformationStep implements Transformation, TaskDependencyContainer {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformationStep.class);

    private final Transformer transformer;
    private final TransformerInvocationFactory transformerInvocationFactory;
    private final ProjectInternal owningProject;
    private final InputFingerprinter globalInputFingerprinter;

    public TransformationStep(Transformer transformer, TransformerInvocationFactory transformerInvocationFactory, DomainObjectContext owner, InputFingerprinter globalInputFingerprinter) {
        this.transformer = transformer;
        this.transformerInvocationFactory = transformerInvocationFactory;
        this.globalInputFingerprinter = globalInputFingerprinter;
        this.owningProject = owner.getProject();
    }

    public Transformer getTransformer() {
        return transformer;
    }

    @Nullable
    public ProjectInternal getOwningProject() {
        return owningProject;
    }

    @Override
    public int stepsCount() {
        return 1;
    }

    public Deferrable<Try<TransformationSubject>> createInvocation(TransformationSubject subjectToTransform, TransformUpstreamDependencies upstreamDependencies, @Nullable NodeExecutionContext context) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transformer.getDisplayName());
        }

        InputFingerprinter inputFingerprinter = context != null ? context.getService(InputFingerprinter.class) : globalInputFingerprinter;

        Try<ArtifactTransformDependencies> resolvedDependencies = upstreamDependencies.computeArtifacts();
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
                    return transformerInvocationFactory.createInvocation(transformer, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                        .map(result -> result.map(subjectToTransform::createSubjectFromResult));
                }
            })
            .getOrMapFailure(failure -> Deferrable.completed(Try.failure(failure)));
    }

    private Try<TransformationSubject> doTransform(TransformationSubject subjectToTransform, InputFingerprinter inputFingerprinter, ArtifactTransformDependencies dependencies, ImmutableList<File> inputArtifacts) {
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        for (File inputArtifact : inputArtifacts) {
            Try<ImmutableList<File>> result = transformerInvocationFactory
                .createInvocation(transformer, inputArtifact, dependencies, subjectToTransform, inputFingerprinter)
                .completeAndGet();

            if (result.getFailure().isPresent()) {
                return Cast.uncheckedCast(result);
            }
            builder.addAll(result.get());
        }
        return Try.successful(subjectToTransform.createSubjectFromResult(builder.build()));
    }

    public void isolateParametersIfNotAlready() {
        transformer.isolateParametersIfNotAlready();
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
        return transformer.getDisplayName();
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        transformer.visitDependencies(context);
    }
}
