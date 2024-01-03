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

package org.gradle.tooling.events.problems;

import org.gradle.api.Incubating;

import java.util.List;

/**
 * A hierarchical representation for a particular problem type.
 * <p>
 * A category object follows a pattern similar to URNs, it has a namespace, a main category and a subcategory.
 * <p>
 * The namespace contains information about the origin of the problem: whether it comes from the Gradle core runtime, or from a third-party plugin.
 * For example, {@code deprecation}, or {@code compilation} is a main category.
 * <p>
 * Subcategories can be an arbitrary list of strings, that ideally serve as a unique identifier.
 * To use compilation as an example, {@code [java, unused-variable]} would be a subcategory, that along with the {@code [compilation]} main category, denotes a particular compiler warning.
 * The exact definition of subcategories depends on the problem's domain.
 * <p>
 *
 * @since 8.6
 */
@Incubating
public interface ProblemCategory {
    /**
     * Returns the namespace. Describes the component reporting the problem (Gradle core or plugin ID).
     *
     * The currently used namespace for Gradle core is {@code org.gradle}.
     * if the problem is reported by a plugin, the namespace should be the plugin ID.
     *
     * @return the problem's namespace.
     * @since 8.6
     */
    String getNamespace();

    /**
     * The main problem category.
     *
     * Currently used categories
     * <pre>
     *     - compilation
     *     - deprecation
     *     - dependency-version-catalog
     *     - task-selection
     *     - task-validation
     * </pre>
     *
     * @return The category string.
     * @since 8.6
     */
    String getCategory();

    /**
     * The problem's subcategories.
     *
     * Currently used subcategories
     * <pre>
     *  - dependency-version-catalog
     *     - alias-not-finished
     *     - reserved-alias-name
     *     - catalog-file-does-not-exist
     *     - toml-syntax-error
     *     - too-many-import-files
     *     - too-many-import-invocation
     *     - no-import-files
     *   - deprecation
     *     - build-invocation
     *     - user-code-direct
     *     - user-code-indirect
     *   - compilation
     *     - groovy-dsl:compilation-failed
     *   - type-validation
     *     - property:annotation-invalid-in-context
     *     - property:cannot-use-optional-on-primitive-types
     *     - property:cannot-write-output
     *     - property:conflicting-annotations
     *     - property:ignored-property-must-not-be-annotated
     *     - property:implicit-dependency
     *     - property:incompatible-annotations
     *     - property:incorrect-use-of-input-annotation
     *     - property:input-file-does-not-exist
     *     - property:missing-annotation
     *     - property:missing-normalization-annotation
     *     - property:nested-map-unsupported-key-type
     *     - property:nested-type-unsupported
     *     - property:mutable-type-with-setter
     *     - property:private-getter-must-not-be-annotated
     *     - property:unexpected-input-file-type
     *     - property:unsupported-notation
     *     - property:unknown-implementation
     *     - property:unsupported-value-type
     *     - property:value-not-set
     *     - type:ignored-annotations-on-method
     *     - type:invalid-use-of-type-annotation
     *     - type:not-cacheable-without-reason
     * </pre>
     *
     * @return the subcategories.
     * @since 8.6
     */
    List<String> getSubcategories();
}
