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
 * Common specification for building deprecations
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
     * Declares from what opaqueVersion the deprecated behavior will be removed.
     * <p>
     * <b>Note:</b> use this only when other version patterns are not fitting.
     * When an opaque version is used, we cannot provide additional intelligence in the reports.
     *
     * @param opaqueVersion the version from which the deprecated behavior will be removed. E.g. "version-1.2.3"
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T removedInVersion(String opaqueVersion);

    /**
     * Declares from what version the deprecated behavior will be removed.
     * <p>
     * When using this version, we can provide additional intelligence in the reports like finding lower bounds of versions reported.
     *
     * @param major the major version from which the deprecated behavior will be removed
     * @param minor the minor version from which the deprecated behavior will be removed
     * @param patch the patch version from which the deprecated behavior will be removed
     * @return the fluent builder used to call this
     * @since 8.13
     */
    T removedInVersion(Integer major, @Nullable Integer minor, @Nullable String patch);

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
