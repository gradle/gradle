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
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Try;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * A single transformation step.
 *
 * Transforms a subject by invoking a transformer on each of the subjects files.
 */
public class TransformationStep implements Transformation {
    private static final Logger LOGGER = LoggerFactory.getLogger(TransformationStep.class);

    private final Transformer transformer;
    private final TransformerInvoker transformerInvoker;

    public TransformationStep(Transformer transformer, TransformerInvoker transformerInvoker) {
        this.transformer = transformer;
        this.transformerInvoker = transformerInvoker;
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
    public TransformationSubject transform(TransformationSubject subjectToTransform, ExecutionGraphDependenciesResolver dependenciesResolver) {
        if (subjectToTransform.getFailure() != null) {
            return subjectToTransform;
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Transforming {} with {}", subjectToTransform.getDisplayName(), transformer.getDisplayName());
        }
        ImmutableList<File> primaryInputs = subjectToTransform.getFiles();
        Try<ArtifactTransformDependenciesInternal> dependencies = dependenciesResolver.forTransformer(transformer);
        return dependencies.getFailure().map(failure -> {
            return subjectToTransform.transformationFailed(failure);
        }).orElseGet(() -> {
            ImmutableList.Builder<File> builder = ImmutableList.builder();
            for (File primaryInput : primaryInputs) {
                Try<ImmutableList<File>> result = transformerInvoker.invoke(transformer, primaryInput, dependencies.get(), subjectToTransform);

                if (result.getFailure().isPresent()) {
                    return subjectToTransform.transformationFailed(result.getFailure().get());
                }
                builder.addAll(result.get());
            }
            return subjectToTransform.transformationSuccessful(builder.build());
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TransformationStep that = (TransformationStep) o;
        return transformer.equals(that.transformer);
    }

    @Override
    public int hashCode() {
        return transformer.hashCode();
    }
}
