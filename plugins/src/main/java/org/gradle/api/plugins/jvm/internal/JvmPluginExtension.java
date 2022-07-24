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

/**
 * This extension is shared by JVM plugins (Java, Groovy, Scala, ...)
 * and can be used to configure JVM specific behavior.
 */
public interface JvmPluginExtension {
    /**
     * Provides access to the several handy JVM related utilities.
     */
    JvmEcosystemUtilities getUtilities();

    /**
     * Creates an outgoing configuration and configures it with reasonable defaults.
     * @param name the name of the outgoing configurtion
     * @param configuration the configuration builder, used to describe what the configuration is used for
     * @return an outgoing (consumable) configuration
     */
    Configuration createOutgoingElements(String name, Action<? super OutgoingElementsBuilder> configuration);

    /**
     * Creates a configuration which can be used to resolve dependencies for the JVM
     * ecosystem. It may also be used to create configuration for declaring dependencies,
     * also known as "bucket" configurations (see ResolvableConfigurationBuilder.usingDependencyBucket).
     *
     * The action is used to configure the created <i>resolvable</i> configuration.
     *
     * @param name the name of the configuration
     * @param action the configuration of the resolvable configuration
     * @return the resolvable configuration
     */
    Configuration createResolvableConfiguration(String name, Action<? super ResolvableConfigurationBuilder> action);

    /**
     * Creates a generic "JVM variant", which implies creation of an underlying source set,
     * compile tasks, jar tasks, possibly javadocs and sources jars and allows configuring if such
     * a component has to be published externally.
     *
     * This can be used to create new tests, test fixtures, or any other Java component which
     * needs to live within the same project as the main component.
     *
     * @param name the name of the component to create
     * @param configuration the configuration for the component to be created
     */
    void createJvmVariant(String name, Action<? super JvmVariantBuilder> configuration);
}
