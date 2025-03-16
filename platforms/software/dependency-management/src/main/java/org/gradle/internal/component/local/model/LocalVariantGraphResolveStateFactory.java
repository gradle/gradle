/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.component.local.model;

import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Constructs {@link LocalVariantGraphResolveState} instances advertised by a
 * {@link DefaultLocalComponentGraphResolveState} instance. This allows the component state to
 * source variant data from multiple sources, both lazy and eager.
 */
public interface LocalVariantGraphResolveStateFactory {

    /**
     * Visit all variants in this component that can be selected in a dependency graph.
     *
     * <p>This includes all variants with and without attributes. Variants visited
     * by this method may not be suitable for selection via attribute matching.</p>
     */
    void visitConsumableVariants(Consumer<LocalVariantGraphResolveState> visitor);

    /**
     * Invalidates any caching used for producing variant state.
     */
    void invalidate();

    /**
     * Produces a variant state instance from the configuration with the given {@code name}.
     *
     * @return Null if the variant with the given configuration name does not exist.
     */
    @Nullable
    LocalVariantGraphResolveState getVariantByConfigurationName(String name);

}
