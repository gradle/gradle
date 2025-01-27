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

import javax.annotation.Nullable;

/**
 * Common, shared specification for other deprecation specs to build upon.
 *
 * @param <T> the subtype of the deprecation spec
 * @since 8.13
 */
@Incubating
public interface CommonDeprecationSpec<T extends CommonDeprecationSpec<?>> {

    /**
     * Declares the replacement for the deprecated behavior.
     *
     * @param replacement the replacement for the deprecated behavior
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T replacedBy(String replacement);

    /**
     * Declares in which version the deprecated item will be removed.
     * <p>
     * <b>Note:</b> use this only when other version patterns are not fitting.
     * When an opaque version is used, we cannot provide additional intelligence in the reports.
     *
     * @param opaqueVersion the version from which the deprecated behavior will be removed. E.g. "version-1.2.3"
     * @return the fluent builder used to call this
     * @since 8.13
     * @see OpaqueDeprecatedVersion
     */
    T removedInVersion(String opaqueVersion);

    /**
     * Declares in which version the deprecated item will be removed.
     * <p>
     * This versioning scheme follows the Maven versioning scheme.
     * For example, the version "1.2.3-SNAPSHOT" would be represented as:
     * <ul>
     *     <li>major: 1</li>
     *     <li>minor: 2</li>
     *     <li>patch: 3</li>
     *     <li>qualifier: "SNAPSHOT"</li>
     * </ul>
     * <p>
     * When using this version, we can provide additional intelligence in the reports like finding lower bounds of versions reported.
     *
     * @param major the major component of the version
     * @param minor the minor component of the version
     * @param patch the patch component of the version
     * @param qualifier the qualifier component of the version
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T removedInVersion(Integer major, @Nullable Integer minor, @Nullable Integer patch, @Nullable String qualifier);

    /**
     * Declares an optional reasoning why the deprecation is happening.
     *
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T because(String reason);

    /**
     * A longer, possibly multi-line description of the deprecation.
     *
     * @param details the details of the deprecation
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T withDetails(String details);
}
