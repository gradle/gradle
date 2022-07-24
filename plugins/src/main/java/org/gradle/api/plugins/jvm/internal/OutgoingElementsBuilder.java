/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;

import java.util.Collections;
import java.util.List;

/**
 * A builder to construct an "outgoing elements" configuration, that is to say something
 * which is consumable by other components (other projects or external projects)
 */
@SuppressWarnings("UnusedReturnValue")
public interface OutgoingElementsBuilder {
    /**
     * Sets the description for this outgoing elements
     * @param description the description
     */
    OutgoingElementsBuilder withDescription(String description);

    /**
     * Tells that this elements configuration provides an API
     */
    OutgoingElementsBuilder providesApi();

    /**
     * Tells that this elements configuration provides a runtime
     */
    OutgoingElementsBuilder providesRuntime();

    /**
     * Allows setting the configurations this outgoing elements will inherit from.
     * Those configurations are typically buckets of dependencies
     * @param parentConfigurations the parent configurations
     */
    OutgoingElementsBuilder extendsFrom(Configuration... parentConfigurations);

    /**
     * Allows setting the configurations this outgoing elements will inherit from.
     * Those configurations are typically buckets of dependencies
     * @param parentConfigurations the parent configurations
     */
    OutgoingElementsBuilder extendsFrom(List<Provider<Configuration>> parentConfigurations);

    /**
     * Adds this configuration as a parent configuration
     * @param configuration the parent configuration
     */
    default OutgoingElementsBuilder extendsFrom(Provider<Configuration> configuration) {
        return extendsFrom(Collections.singletonList(configuration));
    }

    /**
     * If this method is called, the outgoing elements configuration will be automatically
     * configured to export the output of the source set.
     * @param sourceSet the source set which consistutes an output to share with this configuration
     */
    OutgoingElementsBuilder fromSourceSet(SourceSet sourceSet);

    /**
     * Registers an artifact to be attached to this configuration. Supports anything supported
     * by the {@link org.gradle.api.artifacts.ConfigurationPublications#artifact(Object)} method,
     * which includes task providers, or files.
     *
     * @param producer the producer
     */
    OutgoingElementsBuilder artifact(Object producer);

    /**
     * Allows refining the attributes of this configuration in case the defaults are not
     * sufficient. The refiner will be called after the default attributes are set.
     * @param refiner the attributes refiner configuration
     */
    OutgoingElementsBuilder providesAttributes(Action<? super JvmEcosystemAttributesDetails> refiner);

    /**
     * Allows declaring the capabilities this outgoing configuration provides
     * @param capabilities the capabilities
     */
    OutgoingElementsBuilder withCapabilities(List<Capability> capabilities);

    /**
     * Explicitly declares a capability provided by this outgoing configuration
     * @param group the capability group
     * @param name the capability name
     * @param version the capability version
     */
    OutgoingElementsBuilder capability(String group, String name, String version);

    /**
     * Configures this outgoing configuration to provides a "classes directory" variant, which
     * is useful for intra and inter-project optimization, avoiding the creation of jar tasks
     * when the only thing which is required is the API of a component.
     * This should only be called in association with {@link #fromSourceSet(SourceSet)}
     */
    OutgoingElementsBuilder withClassDirectoryVariant();

    /**
     * Configures this outgoing variant for publication. A published outgoing variant
     * configured this way will be mapped to the "optional" scope, meaning that its
     * dependencies will appear as optional in the generated POM file.
     */
    OutgoingElementsBuilder published();
}
