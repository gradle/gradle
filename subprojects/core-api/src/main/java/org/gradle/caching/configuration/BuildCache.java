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

import org.gradle.api.provider.Property;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;

/**
 * Configuration object for a build cache.
 *
 * @since 3.5
 */
public interface BuildCache {

    /**
     * Returns whether the build cache is enabled.
     */
    @ReplacesEagerProperty
    Property<Boolean> getEnabled();

    /**
     * Returns whether a given build can store outputs in the build cache.
     */
    @ToBeReplacedByLazyProperty
    boolean isPush();

    /**
     * Sets whether a given build can store outputs in the build cache.
     */
    void setPush(boolean enabled);
}
