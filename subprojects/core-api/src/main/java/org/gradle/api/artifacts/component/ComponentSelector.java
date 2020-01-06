/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.artifacts.component;

import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.List;

/**
 * Represents some opaque criteria used to select a component instance during dependency resolution. Various sub-interfaces
 * expose specific details about the criteria.
 *
 * @since 1.10
 */
@UsedByScanPlugin
public interface ComponentSelector {
    /**
     * Returns a human-consumable display name for this selector.
     *
     * @return Display name
     * @since 1.10
     */
    String getDisplayName();

    /**
     * Checks if selector matches component identifier.
     *
     * @param identifier Component identifier
     * @return if this selector matches exactly the given component identifier.
     * @since 1.10
     */
    boolean matchesStrictly(ComponentIdentifier identifier);

    /**
     * The attributes of the module to select the component from. The attributes only include
     * selector specific attributes. This means it typically doesn't include any consumer specific attribute.
     *
     * @return the attributes
     *
     * @since 4.9
     */
    AttributeContainer getAttributes();

    /**
     * The requested capabilities.
     * @return the requested capabilities. If returning an empty list, the implicit capability will be used.
     *
     * @since 5.3
     */
    List<Capability> getRequestedCapabilities();
}
