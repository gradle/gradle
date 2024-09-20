/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.resolution.failure.transform;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvedVariant;
import org.gradle.api.internal.artifacts.transform.Transform;
import org.gradle.api.internal.artifacts.transform.TransformStep;
import org.gradle.api.internal.artifacts.transform.TransformedVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;

import javax.inject.Inject;
import java.util.List;

/**
 * This type is responsible for converting from heavyweight {@link TransformedVariant} instances to
 * lightweight {@link TransformationChainData} instances.
 * <p>
 * See the {@link org.gradle.internal.component.resolution.failure.transform package javadoc} for why.
 */
public final class TransformedVariantConverter {
    private final ImmutableAttributesFactory attributesFactory;

    @Inject
    public TransformedVariantConverter(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    public ImmutableList<TransformationChainData> convert(List<TransformedVariant> transformedVariants) {
        ImmutableList.Builder<TransformationChainData> builder = ImmutableList.builder();
        transformedVariants.forEach(transformedVariant -> builder.add(convert(transformedVariant)));
        return builder.build();
    }

    private TransformationChainData convert(TransformedVariant transformedVariant) {
        AttributeChangeRecordingVisitor visitor = new AttributeChangeRecordingVisitor(transformedVariant.getRoot());
        transformedVariant.getTransformChain().visitTransformSteps(visitor);
        return visitor.buildResultData();
    }

    private final class AttributeChangeRecordingVisitor implements Action<TransformStep> {
        private final ImmutableList.Builder<TransformData> stepsBuilder = ImmutableList.builder();

        private final SourceVariantData sourceVariant;
        private ImmutableAttributes currentAttributes;

        public AttributeChangeRecordingVisitor(ResolvedVariant root) {
            sourceVariant = new SourceVariantData(root.asDescribable().getDisplayName(), root.getAttributes());
            currentAttributes = sourceVariant.getAttributes();
        }

        @Override
        public void execute(TransformStep transformStep) {
            TransformData transformData = convert(currentAttributes, transformStep);
            stepsBuilder.add(transformData);
            currentAttributes = transformData.getFromAttributes();
        }

        public TransformationChainData buildResultData() {
            return new TransformationChainData(sourceVariant, stepsBuilder.build());
        }

        private TransformData convert(ImmutableAttributes currentAttributes, TransformStep step) {
            Transform transform = step.getTransform();
            ImmutableAttributes resultingToAttributes = attributesFactory.join(currentAttributes, transform.getToAttributes()).asImmutable();
            return new TransformData(transform.getDisplayName(), currentAttributes, resultingToAttributes);
        }
    }
}
