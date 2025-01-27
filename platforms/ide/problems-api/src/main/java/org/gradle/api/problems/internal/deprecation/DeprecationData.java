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

package org.gradle.api.problems.internal.deprecation;


import org.gradle.api.Incubating;
import org.gradle.api.problems.AdditionalData;
import org.gradle.api.problems.deprecation.DeprecatedVersion;

import javax.annotation.Nullable;

/**
 * Additional data shipped with a deprecation.
 * <p>
 * This data is accessible, after reporting the deprecation, to consumers of problem events.
 *
 * @since 8.13
 */
@Incubating
public interface DeprecationData extends AdditionalData {
    /**
     * Returns the version in which the deprecation will become an error.
     *
     * @return the version in which the deprecation will become an error.
     * @since  8.13
     */
    @Nullable
    DeprecatedVersion getRemovedIn();

    /**
     * Returns the feature that replaces the deprecated feature.
     *
     * @return the feature that replaces the deprecated feature.
     */
    @Nullable
    String getReplacedBy();

    /**
     * Returns human-readable reason of why the deprecation was introduced.
     *
     * @return reason of why the deprecation was introduced
     */
    @Nullable
    String getBecause();
}
