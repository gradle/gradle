/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins.internal;

import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Usage;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.component.UsageContext;

import java.util.Set;

public abstract class AbstractUsageContext implements UsageContext {
    private final ImmutableAttributes attributes;
    private final Set<PublishArtifact> artifacts;

    public AbstractUsageContext(ImmutableAttributes attributes, Set<PublishArtifact> artifacts) {
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    @Override
    public Usage getUsage() {
        throw new UnsupportedOperationException("This method has been deprecated, should never be called");
    }

    @Override
    public AttributeContainer getAttributes() {
        return attributes;
    }

    @Override
    public Set<PublishArtifact> getArtifacts() {
        return artifacts;
    }
}
