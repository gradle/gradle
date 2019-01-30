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
package org.gradle.api.component;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;

import java.util.Set;

@Incubating
public interface MutableSoftwareComponentVariant {

    AttributeContainer getSourceAttributes();

    Set<? extends Capability> getSourceCapabilities();

    /**
     * Specifies the variant attributes. If this method is not called,
     * the attributes of the source will be used.
     *
     * @param configuration the configuration action
     */
    void attributes(Action<? super AttributeContainer> configuration);

    /**
     * Allows mutation of the capabilities of this variant.
     * @param configuration the configuration
     */
    void capabilities(Action<? super MutableCapabilitiesMetadata> configuration);
}
