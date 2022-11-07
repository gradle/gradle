/*
 * Copyright 2022 the original author or authors.
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
package org.gradle.api.internal.component;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.component.SoftwareComponentVariant;

import java.util.Set;

public abstract class AbstractSoftwareComponentVariant implements SoftwareComponentVariant {
    private final AttributeContainer attributes;
    private final Set<? extends PublishArtifact> artifacts;

    public AbstractSoftwareComponentVariant(AttributeContainer attributes, Set<? extends PublishArtifact> artifacts) {
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public Set<? extends PublishArtifact> getArtifacts() {
        return artifacts;
    }
}
