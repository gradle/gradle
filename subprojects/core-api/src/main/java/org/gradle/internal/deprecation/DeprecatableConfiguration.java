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

package org.gradle.internal.deprecation;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.Configuration;

import java.util.List;

/**
 * This internal interface extends {@link Configuration} adding functionality to allow plugins to deprecate
 * configurations.
 * <p>
 * This interface also contains a few methods unrelated to deprecation, but which need to be available to
 * other gradle subprojects.  These methods include:
 * <ul>
 *     <li>{@link #preventUsageMutation()}</li>
 *     <li>{@link #setCanBeDeclaredAgainst(boolean)}</li>
 *     <li>{@link #isCanBeDeclaredAgainst()}</li>
 * </ul>
 * These methods would be better suited for the base {@link Configuration} interface, or the (inaccessible from this project)
 * {@link org.gradle.api.internal.artifacts.configurations.ConfigurationInternal ConfigurationInternal}
 * interface, but we want to hide them from the public API.
 */
@SuppressWarnings("JavadocReference")
public interface DeprecatableConfiguration extends Configuration {

    /**
     * Get configurations that should be used to declare dependencies instead of this configuration.
     *
     * <p>The returned value is undefined if the configuration is not deprecated for declaration.</p>
     */
    List<String> getDeclarationAlternatives();

    /**
     * Get configurations that should be used to consume a component instead of consuming this configuration.
     *
     * <p>The returned value is undefined if the configuration is not deprecated for resolution.</p>
     */
    List<String> getResolutionAlternatives();

    /**
     * Set the configuration which should be used for dependency declaration instead of this configuration.
     * Does nothing if this configuration is not deprecated for declaration.
     */
    void addDeclarationAlternatives(String... alternativesForDeclaring);

    /**
     * Set the configuration which should be used for dependency resolution instead of this configuration.
     * Does nothing if this configuration is not deprecated for resolution.
     */
    void addResolutionAlternatives(String... alternativesForResolving);

    /**
     * If this configuration is deprecated for consumption, emit a deprecation warning.
     */
    default void maybeEmitConsumptionDeprecation() {
        if (isDeprecatedForConsumption()) {
            DeprecationLogger.deprecateConfiguration(getName())
                .forConsumption()
                .willBecomeAnErrorInGradle9()
                .withUserManual("dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
    }

    /**
     * If this configuration is deprecated for declaration, emit a deprecation warning.
     */
    default void maybeEmitDeclarationDeprecation() {
        if (isDeprecatedForDeclarationAgainst()) {
            DeprecationLogger.deprecateConfiguration(getName())
                .forDependencyDeclaration()
                .replaceWith(getDeclarationAlternatives())
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
    }

    /**
     * If this configuration is deprecated for resolution, emit a deprecation warning.
     */
    default void maybeEmitResolutionDeprecation() {
        if (isDeprecatedForResolution()) {
            DeprecationLogger.deprecateConfiguration(getName()
                ).forResolution()
                .replaceWith(getResolutionAlternatives())
                .willBecomeAnErrorInGradle9()
                .withUpgradeGuideSection(5, "dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations")
                .nagUser();
        }
    }

    boolean isDeprecatedForConsumption();
    boolean isDeprecatedForResolution();
    boolean isDeprecatedForDeclarationAgainst();

    default boolean canSafelyBeResolved() {
        return isCanBeResolved() && !isDeprecatedForResolution();
    }

    /**
     * Prevents any calls to methods that change this configuration's allowed usage (e.g. {@link #setCanBeConsumed(boolean)},
     * {@link #setCanBeResolved(boolean)}, {@link #setCanBeDeclaredAgainst(boolean)}) from succeeding; and causes them
     * to throw an exception.
     */
    void preventUsageMutation();

    /**
     * Configures if a configuration can have dependencies declared upon it.
     *
     * @since 8.0
     */
    @Incubating
    void setCanBeDeclaredAgainst(boolean allowed);

    /**
     * Returns true if it is allowed to declare dependencies upon this configuration.
     * Defaults to true.
     * @return true if this configuration can have dependencies declared
     *
     * @since 8.0
     */
    @Incubating
    boolean isCanBeDeclaredAgainst();
}
