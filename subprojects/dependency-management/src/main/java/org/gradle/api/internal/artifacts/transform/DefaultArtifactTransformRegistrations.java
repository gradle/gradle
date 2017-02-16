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
import org.gradle.api.artifacts.transform.ArtifactTransformRegistration;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;

import java.util.List;
import java.io.File;

public class DefaultArtifactTransformRegistrations implements ArtifactTransformRegistrationsInternal {
    private final List<RegisteredArtifactTransform> transforms = Lists.newArrayList();
    private final File outputDir;
    private final ImmutableAttributesFactory immutableAttributesFactory;

    public DefaultArtifactTransformRegistrations(File outputDir, ImmutableAttributesFactory immutableAttributesFactory) {
        this.outputDir = outputDir;
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    @Override
    public void registerTransform(Action<? super ArtifactTransformRegistration> registrationAction) {
        RecordingRegistration reg = new RecordingRegistration();
        registrationAction.execute(reg);

        for (RegisteredArtifactTransform transformRegistration : transforms) {
            if (transformRegistration.getType() == reg.type
                && transformRegistration.getFrom() == reg.getFrom()
                && transformRegistration.getTo() == reg.getTo()) {
                return; //already registered
            }
        }

        RegisteredArtifactTransform registration = new RegisteredArtifactTransform(ImmutableAttributes.of(reg.from), ImmutableAttributes.of(reg.to), reg.type, reg.config, outputDir);
        transforms.add(registration);
    }

    public Iterable<RegisteredArtifactTransform> getTransforms() {
        return transforms;
    }

    private class RecordingRegistration implements ArtifactTransformRegistration {
        final AttributeContainerInternal from = new DefaultMutableAttributeContainer(immutableAttributesFactory);
        final AttributeContainerInternal to = new DefaultMutableAttributeContainer(immutableAttributesFactory);
        Class<? extends ArtifactTransform> type;
        Action<? super ArtifactTransform> config;

        @Override
        public AttributeContainer getFrom() {
            return from;
        }

        @Override
        public AttributeContainer getTo() {
            return to;
        }

        @Override
        public void artifactTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
            this.type = type;
            this.config = config;
        }
    }
}
