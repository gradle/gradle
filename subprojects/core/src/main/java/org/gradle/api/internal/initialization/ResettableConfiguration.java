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

package org.gradle.api.internal.initialization;

import org.gradle.api.artifacts.Configuration;

/**
 * A {@link Configuration} which can reset its resolution state. This interface is
 * separate from {@code ConfigurationInternal} since that interface lives in
 * {@code :dependency-management} and is not accessible from {@code :core}.
 */
public interface ResettableConfiguration extends Configuration {

    /**
     * Reset this Configuration's resolution state to the unresolved state, discarding any
     * cached resolution results. No other state is reconfigured. The configuration remains
     * immutable if resolution has already occurred. Any hooks or actions which ran when the
     * configuration was originally resolved may or may not run again if this configuration
     * is resolved again. Any side-effects or external caching which occur as a part of or
     * after this configuration's resolution are not reversed or invalidated.
     * <p>
     * <strong>This method should be avoided if at all possible, and should only be used as a last resort.</strong>
     * This method was originally added in order to release the resources of the {@code classpath}
     * configurations used for resolving buildscript classpaths, as they consumed a non-negligible
     * amount of memory even after the buildscript classpath was assembled.
     * <p>
     * Future work in this area should remove the need of this method by instead caching resolution
     * results to disk and therefore freeing the memory used to cache a configuration's
     * resolution result. This new functionality should be applicable to all configurations -- those
     * used during buildscript classpath loading and normal configuration-time configurations.
     * By applying this functionality to all configurations, this method would no longer be required.
     *
     * @implSpec Usage: This method should only be called on resolvable configurations and should throw an exception if
     * called on a configuration that does not permit this usage.
     */
    void resetResolutionState();

}
