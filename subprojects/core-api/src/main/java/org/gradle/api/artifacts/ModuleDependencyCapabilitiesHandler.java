/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.artifacts;

import org.gradle.api.provider.Provider;
import org.gradle.internal.HasInternalProtocol;

/**
 * The capabilities requested for a dependency. This is used in variant-aware dependency
 * management, to select only variants which provide the requested capabilities. By
 * default, Gradle will only look for variants which provide the "implicit" capability,
 * which corresponds to the GAV coordinates of the component. If the user calls methods
 * on this handler, then the requirements change and explicit capabilities are required.
 *
 * @since 5.3
 */
@HasInternalProtocol
public interface ModuleDependencyCapabilitiesHandler {
    /**
     * Requires a single capability.
     * @param capabilityNotation the capability notation (e.g. group:name:version), {@linkplain Provider Providers} are also accepted
     */
    void requireCapability(Object capabilityNotation);

    /**
     * Requires multiple capabilities. The selected variants MUST provide ALL of them
     * to be selected.
     * @param capabilityNotations the capability notations (e.g. group:name:version), {@linkplain Provider Providers} are also accepted
     */
    void requireCapabilities(Object... capabilityNotations);
}
