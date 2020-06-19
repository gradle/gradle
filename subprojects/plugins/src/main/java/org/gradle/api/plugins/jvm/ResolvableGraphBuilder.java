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
package org.gradle.api.plugins.jvm;

import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;

import java.util.Collections;
import java.util.List;

/**
 * An incoming configuration builder. Such a configuration is meant
 * to be resolved.
 *
 * @since 6.6
 */
@Incubating
public interface ResolvableGraphBuilder {
    /**
     * Also create the dependency bucket of the provided name and
     * make the resolvable configuration extend from it.
     * @param name the name of the bucket of dependencies
     */
    ResolvableGraphBuilder usingDependencyBucket(String name);

    /**
     * Configures the resolution for runtime of java libraries
     */
    ResolvableGraphBuilder requiresJavaLibrariesRuntime();

    /**
     * Configures the resolution for API of java libraries
     */
    ResolvableGraphBuilder requiresJavaLibrariesAPI();

    /**
     * Adds configurations the resolvable configuration should extend from.
     * Those configurations should be typically buckets of dependencies
     * @param parentConfigurations the parent configurations
     */
    ResolvableGraphBuilder extendsFrom(Configuration... parentConfigurations);

    /**
     * Adds configurations the resolvable configuration should extend from.
     * Those configurations should be typically buckets of dependencies
     * @param parentConfigurations the parent configurations
     */
    ResolvableGraphBuilder extendsFrom(List<Provider<Configuration>> parentConfigurations);

    /**
     * Adds this configuration as a parent configuration of the resolvable configuration
     * @param configuration the parent configuration
     */
    default ResolvableGraphBuilder extendsFrom(Provider<Configuration> configuration) {
        return extendsFrom(Collections.singletonList(configuration));
    }

    /**
     * Allows refining the attributes of this configuration in case the defaults are not
     * sufficient. The refiner will be called after the default attributes are set.
     * @param refiner the attributes refiner configuration
     */
    ResolvableGraphBuilder attributes(Action<? super JvmEcosystemAttributesDetails> refiner);
}
