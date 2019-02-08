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
import org.gradle.api.artifacts.Configuration;

import javax.annotation.Nullable;

/**
 * A component which can declare additional variants corresponding to
 * features. When published to Maven POMs, the dependencies of those variants
 * are exposed as optional dependencies. When published to Gradle metadata, the
 * variants are published as is.
 *
 * @since 5.3
 */
@Incubating
public interface AdhocComponentWithVariants extends ComponentWithEcosystems {

    /**
     * Declares an additional variant to publish, corresponding to an additional feature.
     * @param outgoingConfiguration the configuration corresponding to the variant to use as source of dependencies and artifacts
     * @param action the action to execute in order to determine if a configuration variant should be published or not
     */
    void addVariantsFromConfiguration(Configuration outgoingConfiguration, Action<? super ConfigurationVariantDetails> action);

    /**
     * By default, an adhoc component will be published with a list of ecosystems corresponding
     * to the ecosystems which have been registered on the project. If for some reason the default
     * ecosystems are not suitable, this method can be called, in which case the defaults are ignored.
     *
     * It may be the case when a plugin registers an ecosystem which is only suitable for testing,
     * but should not be used when publishing.
     *
     * @param name the name of the ecosystem
     * @param description a description of the ecosystem
     */
    void registerEcosystem(String name, @Nullable String description);
}
