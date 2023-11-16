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

package org.gradle.internal.component.model;

import org.gradle.api.attributes.HasAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.component.external.model.ImmutableCapabilities;

import java.util.List;
import java.util.Set;

/**
 * Immutable metadata for a component variant instance that is used to perform dependency graph resolution.
 *
 * <p>Note that this metadata does not provide any information about the available artifacts of this variants, as this may be expensive to resolve.
 * Information about the artifacts can be accessed via the methods of {@link ComponentGraphResolveState}.</p>
 */
public interface VariantGraphResolveMetadata extends HasAttributes {
    /**
     * Returns the name for this variant, which is unique for the variants of its owning component.
     */
    String getName();

    ImmutableAttributes getAttributes();

    /**
     * Returns the "sub variants" of this variant.
     *
     * <p>This concept should disappear.</p>
     */
    Set<? extends Subvariant> getVariants();

    List<? extends DependencyMetadata> getDependencies();

    List<? extends ExcludeMetadata> getExcludes();

    ImmutableCapabilities getCapabilities();

    boolean isTransitive();

    boolean isExternalVariant();

    interface Subvariant {
        String getName();

        ImmutableAttributes getAttributes();

        ImmutableCapabilities getCapabilities();
    }
}
