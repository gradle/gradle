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

package org.gradle.api.problems;

import org.gradle.api.Incubating;

/**
 * {@link Problem} instance builder requiring the specification of the problem category.
 *
 * @since 8.5
 */
@Incubating
public interface ProblemBuilderDefiningCategory {

    /**
     * Declares the problem category.
     *
     * It offers a hierarchical categorization with arbitrary details.
     * Clients must declare a main category string. Freeform with the following conventions and limitations.
     * Subcategories can be optionally specified with arbitrary details. The same conventions and limitations apply.
     * When a problem is created (with BuildableProblemBuilder.build()) the category can be obtained with {@link Problem}.
     * The `ProblemCategory` then represents a local category with some namespace, distinguishing built-in and third-party categories.
     * Example:
     * {@code category("validation", "missing-input") }

     * @param category the type name
     * @param details the type details
     * @return the builder for the next required property

     * @see ProblemCategory
     */
    ProblemBuilder category(String category, String... details);
}
