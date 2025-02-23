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

package org.gradle.api.problems.deprecation;

import org.gradle.api.Incubating;

/**
 * Common, shared specification for other deprecation specs to build upon.
 *
 * @param <T> the subtype of the deprecation spec
 * @since 8.14
 */
@Incubating
public interface CommonDeprecationSpec<T extends CommonDeprecationSpec<?>> {

    /**
     * Declares the replacement for the deprecated behavior.
     *
     * @param replacement the replacement for the deprecated behavior
     * @return the fluent builder used to call this
     * @since 8.14
     */
    T replacedBy(String replacement);

    /**
     * Declares in which version the deprecated item will be removed.
     * <p>
     * <b>Note:</b> use this only when other version patterns are not fitting.
     * When an opaque version is used, we cannot provide additional intelligence in the reports.
     *
     * @param version the version from which the deprecated behavior will be removed. E.g. "version-1.2.3"
     * @return the fluent builder used to call this
     * @since 8.14
     * @see OpaqueDeprecatedVersion
     */
    T removedInVersion(String version);

    /**
     * Declares longer - possibly multi-line - description of the deprecation. an optional reasoning why the deprecation is happening.
     * Will be reported as a problem details
     *
     * @return the fluent builder used to call this
     * @since 8.14
     */
    T because(String reason);
}
