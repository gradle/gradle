/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;

import java.util.function.Function;

/**
 * Represents a parent configuration that is extended from, which may be either a realized configuration or a provider for a configuration.
 */
public interface ExtendedConfiguration {
    /**
     * Returns the underlying configuration. Calling this method may realize the configuration if it is not already realized.
     */
    Configuration get();

    /**
     * Maps this extended configuration to a provider of a domain object collection.  Calling this method should not
     * realize the configuration if it is not already realized.
     *
     * @param configurationToCollection Function that maps the configuration to a domain object collection
     * @param <T>                       The type of objects in the domain object collection
     * @return A provider of the domain object collection specific by {@code configurationToCollection}
     */
    <T> Provider<DomainObjectCollection<? extends T>> mapToCollection(Function<Configuration, DomainObjectCollection<T>> configurationToCollection);

    /**
     * Visitor interface for visiting extended configurations (see {@link ExtendedConfigurations#visitConfigurations(Visitor)}).
     */
    interface Visitor {
        void visit(ExtendedConfiguration configuration);
    }
}
