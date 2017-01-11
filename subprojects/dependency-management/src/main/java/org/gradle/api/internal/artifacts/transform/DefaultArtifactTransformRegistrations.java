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
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.DefaultMutableAttributeContainer;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.reflect.DirectInstantiator;

import java.util.List;

public class DefaultArtifactTransformRegistrations implements ArtifactTransformRegistrationsInternal {
    private final List<ArtifactTransformRegistration> transforms = Lists.newArrayList();
    private final ImmutableAttributesFactory immutableAttributesFactory;

    public DefaultArtifactTransformRegistrations(ImmutableAttributesFactory immutableAttributesFactory) {
        this.immutableAttributesFactory = immutableAttributesFactory;
    }

    public void registerTransform(Class<? extends ArtifactTransform> type, Action<? super ArtifactTransform> config) {
        for (ArtifactTransformRegistration transformRegistration : transforms) {
            if (transformRegistration.getType() == type) {
                return; //already registered
            }
        }
        ArtifactTransform artifactTransform = DirectInstantiator.INSTANCE.newInstance(type);
        AttributeContainerInternal from = new DefaultMutableAttributeContainer(immutableAttributesFactory);

        org.gradle.api.internal.artifacts.dsl.dependencies.DefaultArtifactTransformTargets registry = new org.gradle.api.internal.artifacts.dsl.dependencies.DefaultArtifactTransformTargets(immutableAttributesFactory);
        artifactTransform.configure(from, registry);

        for (AttributeContainerInternal to : registry.getNewTargets()) {
            ArtifactTransformRegistration registration = new ArtifactTransformRegistration(from.asImmutable(), to.asImmutable(), type, config);
            transforms.add(registration);
        }
    }

    public Iterable<ArtifactTransformRegistration> getTransforms() {
        return transforms;
    }
}
