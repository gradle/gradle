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
 * This interface provides a consolidated and finalized view of the related feature flags.
 * The features themselves can be requested via dedicated build options or properties.
 *
 * @see BuildFeatures
 * @since 8.5
 */
@Incubating
public interface BuildFeature {

    /**
     * Whether the feature was requested for the build.
     * <p>
     * This method is primarily useful for gathering feature usage statistics.
     * <p>
     * Note that when a feature is requested, it may still be effectively disabled due to various reasons.
     * In case an effective value is needed, use {@link #getActive()}.
     */
    Provider<Boolean> getRequested();

    /**
     * Whether the feature is active in the build.
     * <p>
     * This method is primarily useful for conditional logic in plugins or build scripts.
     * For instance, optional features of a plugin could be disabled if they are incompatible with a given build feature.
     * <p>
     * Note that when a feature is requested, it may still be effectively disabled due to various reasons.
     * This can be caused by other build features or build options requested for the build.
     */
    Provider<Boolean> getActive();

}

