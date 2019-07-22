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

import org.gradle.api.artifacts.ConfigurationVariant;
import org.gradle.internal.HasInternalProtocol;

/**
 * The details object used to determine what to do with a
 * configuration variant when publishing.
 *
 * @since 5.3
 */
@HasInternalProtocol
public interface ConfigurationVariantDetails {
    /**
     * The configuration variant
     */
    ConfigurationVariant getConfigurationVariant();

    /**
     * Marks this configuration variant as being skipped when publishing.
     */
    void skip();

    /**
     * Provides information about the optional status of this added configuration variant.
     *
     * <ul>
     *     <li>For the Maven world, this means that dependencies will be declared as {@code optional}.</li>
     *     <li>For the Ivy world, this means that configuration marked optional will not be extended by the {@code default} configuration.</li>
     * </ul>
     */
    void mapToOptional();

    /**
     * Provides information about how to publish to a Maven POM file. If
     * nothing is said, Gradle will publish all dependencies from this
     * configuration to the <i>compile</i> scope. It is preferable to give
     * a mapping in order for consumers to get the right dependencies when
     * asking for the API or the runtime of a component published as a POM
     * file only.
     *
     * Note that Gradle will write Maven scopes in the following order:
     *
     * <ul>
     *     <li>compile dependencies</li>
     *     <li>runtime dependencies</li>
     *     <li>optional compile dependencies</li>
     *     <li>optional runtime dependencies</li>
     * </ul>
     *
     * The mapping only applies to dependencies: dependency constraints will
     * systematically be published as import scope.
     *  @param scope the Maven scope to use for dependencies found in this configuration variant
     *
     */
    void mapToMavenScope(String scope);
}
