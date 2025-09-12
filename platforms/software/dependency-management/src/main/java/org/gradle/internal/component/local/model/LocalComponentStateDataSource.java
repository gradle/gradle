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

import org.gradle.api.model.internal.DataModel;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Acts as a source of data for local components, so that the component state instance
 * can be constructed without needing to know the details of how the data is sourced.
 */
public interface LocalComponentStateDataSource {

    /**
     * Visit all variants in this component that can be selected in a dependency graph.
     *
     * <p>This includes all variants with and without attributes. Variants visited
     * by this method may not be suitable for selection via attribute matching.</p>
     */
    void visitConsumableVariants(Consumer<LocalVariantGraphResolveState> visitor);

    /**
     * Find the data model for a given name.
     *
     * @return null if no data model with the given name exists.
     */
    @Nullable DataModel findDataModel(String name);
}
