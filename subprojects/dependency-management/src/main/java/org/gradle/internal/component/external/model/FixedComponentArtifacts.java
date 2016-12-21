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

package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifacts;
import org.gradle.internal.component.model.ConfigurationMetadata;
import org.gradle.internal.component.model.DefaultVariantMetadata;
import org.gradle.internal.component.model.VariantMetadata;

import java.util.Set;

/**
 * Uses a fixed set of artifacts for all configurations.
 */
public class FixedComponentArtifacts implements ComponentArtifacts {
    private final Set<ComponentArtifactMetadata> artifacts;

    public FixedComponentArtifacts(Iterable<? extends ComponentArtifactMetadata> artifacts) {
        this.artifacts = ImmutableSet.copyOf(artifacts);
    }

    public Set<ComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }

    @Override
    public Set<? extends VariantMetadata> getVariantsFor(ConfigurationMetadata configuration) {
        return ImmutableSet.of(new DefaultVariantMetadata(AttributeContainerInternal.EMPTY, artifacts));
    }
}
