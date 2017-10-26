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

package org.gradle.internal.component.model;

import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.DisplayName;

import java.util.List;

public class DefaultVariantMetadata implements VariantMetadata {
    private final DisplayName displayName;
    private final AttributeContainerInternal attributes;
    private final List<? extends ComponentArtifactMetadata> artifacts;

    public DefaultVariantMetadata(DisplayName displayName, AttributeContainerInternal attributes, List<? extends ComponentArtifactMetadata> artifacts) {
        this.displayName = displayName;
        this.attributes = attributes;
        this.artifacts = artifacts;
    }

    @Override
    public DisplayName asDescribable() {
        return displayName;
    }

    @Override
    public AttributeContainerInternal getAttributes() {
        return attributes;
    }

    @Override
    public List<? extends ComponentArtifactMetadata> getArtifacts() {
        return artifacts;
    }
}
