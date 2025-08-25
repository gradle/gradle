/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.jvm.internal.services;

import org.gradle.StartParameter;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.StartParameterInternal;
import org.gradle.initialization.JvmToolchainsConfigurationValidator;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;

public class DefaultJvmToolchainsConfigurationValidator implements JvmToolchainsConfigurationValidator {
    private final StartParameterInternal startParameter;

    public DefaultJvmToolchainsConfigurationValidator(StartParameterInternal startParameter) {
        this.startParameter = startParameter;
    }

    /**
     * Validates that the given property is set as a Gradle property.
     *
     * ToolchainBuildOptions takes care of capturing toolchain configuration system properties in the launcher and
     * shipping them to the daemon as project properties in {@link StartParameter}.  So, whether a property is set as
     * a system property or as a project property, it should still be available as a "Gradle property".  Here we check
     * if it was specified as a project property, but not a system property, and, if so, emit a deprecation warning.
     *
     * It's conceivable that it could be set as both a system property and a project property for migration purposes,
     * which is fine.  If so, we ensure that they have the same value, and if not, throw an exception.
     */
    @Override
    public void validatePropertyConfiguration(String propertyName) {
        if (startParameter.getProjectProperties().containsKey(propertyName)) {
            if (System.getProperties().containsKey(propertyName)) {
                if (!startParameter.getProjectProperties().get(propertyName).equals(System.getProperties().get(propertyName))) {
                    throw new InvalidUserDataException(
                        "The Gradle property '" + propertyName + "' (set to '" + System.getProperties().get(propertyName) + "') " +
                            "has a different value than the project property '" + propertyName + "' (set to '" + startParameter.getProjectProperties().get(propertyName) + "')." +
                            " Please set them to the same value or only set the Gradle property."
                    );
                }
            } else {
                throw new InvalidUserDataException("Specifying '" + propertyName + "' as a project property on the command line is no longer supported. " +
                    "Instead, specify it as a Gradle property: '-D" + propertyName + "=" + startParameter.getProjectProperties().get(propertyName) + "'.");
            }
        }
    }

    /**
     * If daemon JVM criteria are configured, validate that all properties related to toolchain discovery are set as Gradle properties.
     */
    @Override
    public void validateAllPropertiesConfigurationsForDaemonJvmToolchains() {
        if (startParameter.isDaemonJvmCriteriaConfigured()) {
            validatePropertyConfiguration(EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY);
            validatePropertyConfiguration(LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY);
            validatePropertyConfiguration(ToolchainConfiguration.AUTO_DETECT);
            validatePropertyConfiguration(AutoInstalledInstallationSupplier.AUTO_DOWNLOAD);
            validatePropertyConfiguration(IntellijInstallationSupplier.INTELLIJ_JDK_DIR_PROPERTY);
        }
    }
}
