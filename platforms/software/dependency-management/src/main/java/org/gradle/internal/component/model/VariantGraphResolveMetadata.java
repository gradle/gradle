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

/**
 * Immutable metadata for a variant of a component, intended for use during graph resolution.
 * <p>
 * This metadata does not provide any information about the available dependencies or artifacts
 * of this variant, as they may be expensive to resolve. Expensive information about this variant
 * can be accessed via the methods of {@link VariantGraphResolveState}.
 */
public interface VariantGraphResolveMetadata extends HasAttributes {

    /**
     * Returns the name for this variant, which is unique for the variants of its owning component.
     *
     * In general, this method should be avoided. The internal engine should not need to know the name of a node and
     * should instead identify nodes based on their integer node ID. This method should only be used for
     * diagnostics/reporting and for implementing existing public API methods that require this field.
     */
    String getName();

    @Override
    ImmutableAttributes getAttributes();

    ImmutableCapabilities getCapabilities();

    boolean isTransitive();

    boolean isExternalVariant();

    /**
     * Returns true if this variant is deprecated and consuming it in a dependency graph should emit a warning. False otherwise.
     */
    default boolean isDeprecated() {
        return false;
    }
}
