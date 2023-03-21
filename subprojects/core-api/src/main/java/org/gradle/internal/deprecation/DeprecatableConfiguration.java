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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

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
     * @return configurations that should be used to declare dependencies instead of this configuration.
     *         Returns 'null' if this configuration is not deprecated for declaration.
     */
    @Nullable
    List<String> getDeclarationAlternatives();

    /**
     * @return deprecation message builder to be used for nagging when this configuration is consumed.
     *         Returns 'null' if this configuration is not deprecated for consumption.
     */
    @Nullable
    DeprecationMessageBuilder.WithDocumentation getConsumptionDeprecation();

    /**
     * @return configurations that should be used to consume a component instead of consuming this configuration.
     *         Returns 'null' if this configuration is not deprecated for resolution.
     */
    @Nullable
    List<String> getResolutionAlternatives();

    /**
     * Allows plugins to deprecate the consumability property (canBeConsumed() == true) of a configuration that will be changed in the next major Gradle version.
     *
     * @param deprecation deprecation message builder to use for nagging upon consumption of this configuration
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForConsumption(Function<DeprecationMessageBuilder.DeprecateConfiguration, DeprecationMessageBuilder.WithDocumentation> deprecation);

    /**
     * Convenience method for {@link #deprecateForConsumption(Function)} which uses the default messaging behavior.
     *
     * @return this configuration
    */
    default DeprecatableConfiguration deprecateForConsumption() {
        return deprecateForConsumption(depSpec -> DeprecationLogger.deprecateConfiguration(getName())
                .forConsumption()
                .willBecomeAnErrorInGradle9()
                .withUserManual("dependencies_should_no_longer_be_declared_using_the_compile_and_runtime_configurations"));
    }

    /**
     * Allows plugins to deprecate the resolvability property (canBeResolved() == true) of a configuration that will be changed in the next major Gradle version.
     *
     * @param alternativesForResolving alternative configurations that can be used for dependency resolution
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForResolution(String... alternativesForResolving);

    /**
     * Allows plugins to deprecate a configuration that will be removed in the next major Gradle version.
     *
     * @param alternativesForDeclaring alternative configurations that can be used to declare dependencies
     * @return this configuration
     */
    DeprecatableConfiguration deprecateForDeclarationAgainst(String... alternativesForDeclaring);

    boolean isDeprecatedForConsumption();
    boolean isDeprecatedForResolution();
    boolean isDeprecatedForDeclarationAgainst();

    default boolean canSafelyBeResolved() {
        if (!isCanBeResolved()) {
            return false;
        }
        List<String> resolutionAlternatives = getResolutionAlternatives();
        return resolutionAlternatives == null;
    }

    /**
     * Prevents any calls to methods that change this configuration's allowed usage (e.g. {@link #setCanBeConsumed(boolean)},
     * {@link #setCanBeResolved(boolean)}, {@link #deprecateForResolution(String...)}) from succeeding; and causes them
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
