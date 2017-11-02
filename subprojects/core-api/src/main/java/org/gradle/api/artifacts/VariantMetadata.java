/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Represents the metadata of one variant of a component, see {@link ComponentMetadataDetails#withVariant(String, Action)}.
 *
 * @since 4.4
 */
@Incubating
public interface VariantMetadata {
    //This interface should extend HasAttributes to allow modification of the variant's attributes

    /**
     * Register a rule that modifies the dependencies of this variant.
     *
     * @param action the action that performs the dependencies adjustment
     */
    void withDependencies(Action<DependenciesMetadata> action);
}
