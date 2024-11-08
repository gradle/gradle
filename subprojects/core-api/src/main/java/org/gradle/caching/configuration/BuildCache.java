/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.configuration;

import org.gradle.api.model.ReplacedBy;
import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;

/**
 * Configuration object for a build cache.
 *
 * @since 3.5
 */
public interface BuildCache {

    /**
     * Controls whether the build cache is enabled.
     *
     * Added for Kotlin source compatibility.
     */
    @ReplacesEagerProperty(originalType = boolean.class)
    Property<Boolean> getEnabled();

    /**
     * Controls whether the build cache is enabled.
     */
    @Deprecated
    @ReplacedBy("getEnabled()")
    default Property<Boolean> getIsEnabled() {
        // TODO: for Gradle 9.0 nag with deprecation once DevelocityConventionsPlugin is updated
        return getEnabled();
    }

    /**
     * Controls whether a given build can store outputs in the build cache.
     */
    @ReplacesEagerProperty(originalType = boolean.class)
    Property<Boolean> getPush();

    /**
     * Controls whether a given build can store outputs in the build cache.
     *
     * Added for Kotlin source compatibility.
     */
    @Deprecated
    @ReplacedBy("getPush()")
    default Property<Boolean> getIsPush() {
        // TODO: for Gradle 9.0 nag with deprecation once DevelocityConventionsPlugin is updated
        return getPush();
    }
}
