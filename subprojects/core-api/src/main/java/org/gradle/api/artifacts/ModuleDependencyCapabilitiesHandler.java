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

import org.gradle.api.Incubating;
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
     *
     * @param capabilityNotation the capability {@linkplain ConfigurationPublications#capability(Object) notation} (e.g. group:name:version), {@linkplain Provider Providers} of any notation are also accepted
     */
    void requireCapability(Object capabilityNotation);

    /**
     * Requires multiple capabilities. The selected variants MUST provide ALL of them
     * to be selected.
     *
     * @param capabilityNotations the capability {@linkplain ConfigurationPublications#capability(Object) notations} (e.g. group:name:version), {@linkplain Provider Providers} of any notation are also accepted
     */
    void requireCapabilities(Object... capabilityNotations);

    /**
     * Require a capability of a component by appending the given suffix to the module name of the resolved component.
     * <p>
     * A capability is derived from a suffix based on the module identity of the component that a dependency
     * resolves to. For example, variant of a component with module identity 'group:name:version' appending the suffix
     * '-test-fixtures' would have a capability 'group:name-test-fixtures:version'.
     *
     * @param suffix The name of the feature to require
     *
     * @since 8.13
     */
    @Incubating
    void requireSuffix(String suffix);

    /**
     * Lazily require a capability of a component by appending the given suffix to the module name of the resolved component.
     *
     * @param suffix The suffix to append to the module name of the resolved component.
     *
     * @see #requireSuffix(String)
     *
     * @since 8.13
     */
    @Incubating
    void requireSuffix(Provider<String> suffix);
}
