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
package org.gradle.api.capabilities;

/**
 * Describes the capabilities of a component in a mutable way.
 * This interface can be used to adjust the capabilities of a published component via
 * metadata rules (see {@link org.gradle.api.artifacts.dsl.ComponentMetadataHandler}.
 *
 * @since 4.7
 */
public interface MutableCapabilitiesMetadata extends CapabilitiesMetadata {

    /**
     * Adds a new capability. If a capability of the same (group, name) is found with a different
     * version, an error will be thrown.
     *
     * @param group the group of the capability
     * @param name the name of the capability
     * @param version the version of the capability
     */
    void addCapability(String group, String name, String version);

    /**
     * Removes a capability.
     * @param group the group of the capability
     * @param name the name of the capability
     */
    void removeCapability(String group, String name);

    /**
     * Returns an immutable vew of the capabilities.
     * @return an immutable view of the capabilities
     */
    CapabilitiesMetadata asImmutable();
}
