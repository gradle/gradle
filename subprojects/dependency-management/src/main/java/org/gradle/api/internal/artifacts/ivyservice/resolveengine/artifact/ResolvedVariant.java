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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import org.gradle.api.Action;
import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.model.VariantResolveMetadata;

import javax.annotation.Nullable;

public interface ResolvedVariant extends HasAttributes {
    DisplayName asDescribable();

    /**
     * An identifier for this variant, if available. A variant may not have an identifier when it represents some ad hoc set of artifacts, for example artifacts declared on a dependency
     * using {@link org.gradle.api.artifacts.ModuleDependency#artifact(Action)} or where individual artifacts have been excluded from the variant.
     */
    @Nullable
    VariantResolveMetadata.Identifier getIdentifier();

    @Override
    AttributeContainerInternal getAttributes();

    ResolvedArtifactSet getArtifacts();

    CapabilitiesMetadata getCapabilities();
}
