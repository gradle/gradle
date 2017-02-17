/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.transform.ArtifactTransform;
import org.gradle.api.artifacts.transform.ArtifactTransformConfiguration;
import org.gradle.api.artifacts.transform.VariantTransform;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.List;

public class DefaultVariantTransforms implements VariantTransforms {
    private final List<RegisteredVariantTransform> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;
    private final Instantiator instantiator;
    private final Factory<File> outputDirectory;

    public DefaultVariantTransforms(Instantiator instantiator, Factory<File> outputDirectory, ImmutableAttributesFactory immutableAttributesFactory) {
        this.instantiator = instantiator;
        this.outputDirectory = outputDirectory;
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    @Override
    public void registerTransform(Action<? super VariantTransform> registrationAction) {
        RecordingRegistration reg = instantiator.newInstance(RecordingRegistration.class, immutableAttributesFactory);
        registrationAction.execute(reg);

        for (RegisteredVariantTransform transformRegistration : transforms) {
            if (transformRegistration.getType() == reg.type
                && transformRegistration.getFrom() == reg.getFrom()
                && transformRegistration.getTo() == reg.getTo()) {
                return; //already registered
            }
        }

        RegisteredVariantTransform registration = new RegisteredVariantTransform(ImmutableAttributes.of(reg.from), ImmutableAttributes.of(reg.to), reg.type, reg.config, outputDirectory.create());
        transforms.add(registration);
    }

    public Iterable<RegisteredVariantTransform> getTransforms() {
        return transforms;
    }

    public static class RecordingRegistration implements VariantTransform {
        final AttributeContainerInternal from;
        final AttributeContainerInternal to;
        Class<? extends ArtifactTransform> type;
        Action<ArtifactTransformConfiguration> config;

        public RecordingRegistration(ImmutableAttributesFactory immutableAttributesFactory) {
            from = new DefaultMutableAttributeContainer(immutableAttributesFactory);
            to = new DefaultMutableAttributeContainer(immutableAttributesFactory);
        }

        @Override
        public AttributeContainer getFrom() {
            return from;
        }

        @Override
        public AttributeContainer getTo() {
            return to;
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type) {
            this.type = type;
            this.config = Actions.doNothing();
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type, Action<ArtifactTransformConfiguration> config) {
            this.type = type;
            this.config = config;
        }
    }
}
