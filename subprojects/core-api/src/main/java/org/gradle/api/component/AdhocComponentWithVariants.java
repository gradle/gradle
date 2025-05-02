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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;

/**
 * A component which can declare additional variants corresponding to
 * features. When published to Maven POMs, the dependencies of those variants
 * are exposed as optional dependencies. When published to Gradle metadata, the
 * variants are published as is.
 *
 * @since 5.3
 */
public interface AdhocComponentWithVariants extends SoftwareComponent {

    /**
     * Declares an additional variant to publish, corresponding to an additional feature.
     * <p>
     * This can be used to determine if the variant should be published or not, and to configure various options specific to the publishing format.
     *
     * @param outgoingConfiguration the configuration corresponding to the variant to use as source of dependencies and artifacts
     * @param action action executed to configure the variant prior to publishing
     */
    void addVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> action);

    /**
     * Further configure previously declared variants.
     * <p>
     * The action can be used to determine if the variant should be published or not, and to configure various options specific to the publishing
     * format.  Note that if multiple actions are added, they are executed in the order they were added.
     *
     * @param outgoingConfiguration the configuration corresponding to the variant to configure with a given action
     * @param action an additional action to be executed to configure the variant prior to publishing
     * @throws InvalidUserDataException if the specified variant was not already added to this component
     */
    void withVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> action);

}
