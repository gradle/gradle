/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;

import javax.annotation.Nullable;

/**
 * Metadata used to resolve artifacts of a component.
 */
public interface ComponentArtifactResolveMetadata {
    ComponentIdentifier getId();

    ModuleVersionIdentifier getModuleVersionId();

    @Nullable
    ModuleSources getSources();

    ImmutableAttributes getAttributes();

    AttributesSchemaInternal getAttributesSchema();

    // Try to avoid using this, it's here to transition away from using ComponentResolveMetadata everywhere
    ComponentResolveMetadata getMetadata();
}
