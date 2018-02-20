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

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.internal.HasInternalProtocol;

/**
 * A capabilities handler can be used to declare the capabilities of a component, or other components.
 * Capabilities represent the "same functionality" and can be used to explain that two components provide
 * the same functionality, but not with the same implementation. A typical example is the implementation of
 * an API (different logging bindings, for example).
 *
 * This supercedes {@link ComponentModuleMetadataHandler component replacement rules}.
 *
 * @since 4.7
 *
 */
@Incubating
@HasInternalProtocol
public interface CapabilitiesHandler {
    /**
     * Configures a capability.
     *
     * @param identifier the identifier of the capability to configure
     * @param configureAction the configuration of the capability
     *
     */
    void capability(String identifier, Action<? super CapabilityHandler> configureAction);
}
