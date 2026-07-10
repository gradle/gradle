/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.tooling.events.problems;

import org.gradle.api.Action;
import org.gradle.api.Incubating;

/**
 * Custom Additional data for a problem.
 * <p>
 * This class allows to access additional data via a custom type that was attached to a problem by using {@link org.gradle.api.problems.ProblemSpec#additionalData(Class, Action)}.
 *
 * @since 8.13
 */
@Incubating
public interface CustomAdditionalData extends AdditionalData {
    /**
     * Returns an instance of the given type that accesses the additional data.
     *
     * @param viewType the view type of the additional data <p>
     *                 This represents the interface or class through which you want to access
     *                 the underlying data. The system will return an implementation of this type
     *                 that provides a specific "view" of the data, allowing for type-safe access
     *                 to underlying data. The view type must be compatible with the actual data structure.
     *                 <p>
     *                 <b>Limitations of the view type:</b>
     *                 <ul>
     *                     <li><b>Allowed types:</b> Only specific types are supported:
     *                         <ul>
     *                             <li>Simple types: {@link String}, primitives and their wrappers ({@link Integer}, {@link Boolean}, etc.)</li>
     *                             <li>Collections: {@link java.util.List}, {@link java.util.Set}, {@link java.util.Map}</li>
     *                             <li>Composites: Types composed of the above allowed types</li>
     *                         </ul>
     *                     </li>
     *                     <li><b>Provider API mapping (Provider API types are not allowed in view types):</b> Provider API types (such as {@link org.gradle.api.provider.Property})
     *                         can be mapped to their corresponding allowed types, e.g. {@code Property<String> getName()} can be represented as {@code String getName()}</li>
     *                 </ul>
     * @since 8.13
     */
    <T> T get(Class<T> viewType);
}
