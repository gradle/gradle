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
import org.gradle.api.tasks.Internal;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor;
import org.gradle.internal.instrumentation.api.annotations.ReplacedAccessor.AccessorType;
import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty.BinaryCompatibility;

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
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isEnabled", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setEnabled", originalType = boolean.class)
        }
    )
    Property<Boolean> getEnabled();

    /**
     * Controls whether the build cache is enabled.
     */
    @ReplacedBy("getEnabled()")
    default Property<Boolean> getIsEnabled() {
        return getEnabled();
    }

    /**
     * This method exists only for Groovy source backward compatibility.
     *
     * @deprecated Use {@link #getEnabled()} instead.
     */
    @Internal
    @Deprecated
    boolean isEnabled();

    /**
     * Controls whether a given build can store outputs in the build cache.
     */
    @ReplacesEagerProperty(
        replacedAccessors = {
            @ReplacedAccessor(value = AccessorType.GETTER, name = "isPush", originalType = boolean.class, binaryCompatibility = BinaryCompatibility.ACCESSORS_KEPT),
            @ReplacedAccessor(value = AccessorType.SETTER, name = "setPush", originalType = boolean.class)
        }
    )
    Property<Boolean> getPush();

    /**
     * Controls whether a given build can store outputs in the build cache.
     *
     * Added for Kotlin source compatibility.
     */
    @ReplacedBy("getPush()")
    default Property<Boolean> getIsPush() {
        return getPush();
    }

    /**
     * This method exists only for Groovy source backward compatibility.
     *
     * @deprecated Use {@link #getPush()} instead.
     */
    @Internal
    @Deprecated
    boolean isPush();
}
