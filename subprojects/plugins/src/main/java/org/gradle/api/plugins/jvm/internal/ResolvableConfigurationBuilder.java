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
import org.gradle.api.provider.Provider;

import java.util.Collections;
import java.util.List;

/**
 * An incoming configuration builder. Such a configuration is meant
 * to be resolved.
 *
 * @since 6.7
 */
public interface ResolvableConfigurationBuilder {

    /**
     * Sets the description of the resolvable configuration this builder creates
     * @param description the description
     */
    ResolvableConfigurationBuilder withDescription(String description);

    /**
     * Also create the dependency bucket of the provided name and
     * make the resolvable configuration extend from it.
     * @param name the name of the bucket of dependencies
     */
    ResolvableConfigurationBuilder usingDependencyBucket(String name);

    /**
     * Also create the dependency bucket of the provided name and
     * make the resolvable configuration extend from it.
     * @param name the name of the bucket of dependencies
     * @param description a description for this dependency bucket
     */
    ResolvableConfigurationBuilder usingDependencyBucket(String name, String description);

    /**
     * Configures the resolution for runtime of java libraries.
     * This is the default if non of requiresJavaLibrariesRuntime() and requiresJavaLibrariesAPI() is used.
     */
    ResolvableConfigurationBuilder requiresJavaLibrariesRuntime();

    /**
     * Configures the resolution for API of java libraries
     */
    ResolvableConfigurationBuilder requiresJavaLibrariesAPI();

    /**
     * Adds configurations the resolvable configuration should extend from.
     * Those configurations should typically be buckets of dependencies.
     * @param parentConfigurations the parent configurations
     */
    ResolvableConfigurationBuilder extendsFrom(Configuration... parentConfigurations);

    /**
     * Adds configurations the resolvable configuration should extend from.
     * Those configurations should typically be buckets of dependencies.
     * @param parentConfigurations the parent configurations
     */
    ResolvableConfigurationBuilder extendsFrom(List<Provider<Configuration>> parentConfigurations);

    /**
     * Adds this configuration as a parent configuration of the resolvable configuration
     * @param configuration the parent configuration
     */
    default ResolvableConfigurationBuilder extendsFrom(Provider<Configuration> configuration) {
        return extendsFrom(Collections.singletonList(configuration));
    }

    /**
     * Allows refining the attributes of this configuration.
     * The refiner will be called after the default attributes are set, depending
     * on calls like {@link #requiresJavaLibrariesAPI()} or {@link #requiresJavaLibrariesRuntime()}.
     *
     * @param refiner the attributes refiner configuration
     */
    ResolvableConfigurationBuilder requiresAttributes(Action<? super JvmEcosystemAttributesDetails> refiner);
}
