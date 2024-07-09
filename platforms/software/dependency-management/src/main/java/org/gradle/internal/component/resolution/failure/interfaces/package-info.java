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

/**
 * This package contains a hierarchy of {@link org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure ResolutionFailure}
 * interfaces that exist to categorize types of failure conditions that can occur during dependency resolution and
 * delineate when during dependency resolution a failure occurred.
 * <p>
 * There are currently 4 different categories of resolution failures processed by the
 * {@link org.gradle.internal.component.resolution.failure.ResolutionFailureHandler ResolutionFailureHandler}.  There may be
 * more in the future.
 * <p>
 * These categories are represented by the immediate children of
 * {@link org.gradle.internal.component.resolution.failure.interfaces.ResolutionFailure ResolutionFailure}:
 * <ol>
 *     <li>{@link org.gradle.internal.component.resolution.failure.interfaces.ComponentSelectionFailure}</li>
 *     <li>{@link org.gradle.internal.component.resolution.failure.interfaces.VariantSelectionFailure}</li>
 *     <li>{@link org.gradle.internal.component.resolution.failure.interfaces.GraphValidationFailure}</li>
 *     <li>{@link org.gradle.internal.component.resolution.failure.interfaces.ArtifactSelectionFailure}</li>
 * </ol>
 * This list is ordered, as the selection of any given artifact can fail the resolution process in many ways.
 * A failure that occurs in any one category in this list will always have the same information available to it.  If a
 * failure occurs in category ordered below it, fixing the failure can <strong>NOT</strong> result in a different failure
 * in an earlier category, but it may "unblock" a latent failure in a later category.
 * <p>
 * All concrete failures in the {@link org.gradle.internal.component.resolution.failure.type} package should
 * implement exactly one of these interfaces.
 */
@org.gradle.api.NonNullApi
package org.gradle.internal.component.resolution.failure.interfaces;
