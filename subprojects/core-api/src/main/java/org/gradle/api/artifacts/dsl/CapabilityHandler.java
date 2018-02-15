/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

import org.gradle.api.Incubating;

/**
 * A single capability handler. A capability is provided by one or more modules.
 * A component <i>may</i> express a preference, but this is not required.
 *
 * @since 4.7
 */
@Incubating
public interface CapabilityHandler {
    /**
     * Declares that this capability is provided by a module.
     * @param moduleIdentifier the module notation (group:name)
     */
    void providedBy(String moduleIdentifier);

    /**
     * Declares that from all modules which provide the capability, the consumer prefers
     * to use the specified module. This is used to disambiguate whenever multiple modules
     * supplying the same capability are found in a dependency graph.
     *
     * @param moduleIdentifer the module identifier of the preferred module for this capability
     *
     * @return the preference
     */
    Preference prefer(String moduleIdentifer);

    /**
     * Wraps information about the preferred choice whenever multiple modules provide the
     * same capability.
     *
     * @since 4.7
     */
    @Incubating
    interface Preference {
        /**
         * Declares a custom reason why this component is preferred.
         * @param reason the reason why this component is preferred
         * @return this preference
         */
        Preference because(String reason);
    }
}
