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
import org.gradle.api.component.SoftwareComponentVariant;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import java.util.Collections;
import java.util.Set;

/**
 * This extends {@link UsageContext} so that we can use it in {@link SoftwareComponentInternal#getUsages()}.
 * Once we remove {@link UsageContext}, this should implement {@link SoftwareComponentVariant} instead.
 */
public abstract class AbstractSoftwareComponentVariant implements UsageContext {
    private final ImmutableAttributes attributes;
    private final Set<? extends PublishArtifact> artifacts;

    public AbstractSoftwareComponentVariant(ImmutableAttributes attributes, Set<? extends PublishArtifact> artifacts) {
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    @Override
    public ImmutableAttributes getAttributes() {
        return attributes;
    }

    @Override
    public Set<? extends PublishArtifact> getArtifacts() {
        return Collections.unmodifiableSet(artifacts);
    }
}
