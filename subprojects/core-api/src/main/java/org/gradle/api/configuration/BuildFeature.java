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

package org.gradle.api.configuration;

import org.gradle.api.Incubating;
import org.gradle.api.provider.Provider;

/**
 * Status of a feature in a build that affects Gradle behavior,
 * and may impose additional requirements on plugins or build scripts.
 * <p>
 * It is possible to check if the feature is {@link #getActive() active} in the current build.
 * The {@link #getRequested() requested} property shows whether the user opted in or opted out from the feature.
 *
 * @see BuildFeatures
 * @since 8.5
 */
@Incubating
public interface BuildFeature {

    /**
     * Whether the feature was requested for the build.
     * <p>
     * The provider <b>can be undefined</b> if the user did not explicitly opt in or opt out from a feature.
     * Use {@link Provider#getOrNull()} to safely retrieve a nullable value or check {@link Provider#isPresent()}.
     * <p>
     * This method is primarily useful for gathering feature usage statistics, as it corresponds to the user intention.
     * <p>
     * Note that the requested state does not always imply that the feature is active in the build.
     * In case an effective status is needed, use {@link #getActive()}.
     *
     * @since 8.5
     */
    Provider<Boolean> getRequested();

    /**
     * Whether the feature is active in the build.
     * <p>
     * The provider is always defined and its value denotes the effective status of a feature in a build.
     * <p>
     * This method is primarily useful for conditional logic in plugins or build scripts.
     * For instance, optional features of a plugin could be disabled if they are incompatible with a given build feature.
     * <p>
     * Note that a feature may be not active even it was requested.
     * This can be caused by other build features or build options requested for the build.
     *
     * @since 8.5
     */
    Provider<Boolean> getActive();

}

