/*
 * Copyright 2024 the original author or authors.
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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;

/**
 * TODO: This class shouldn't exist.
 *
 * Instead of puling from ProviderFactory, the settings for toolchain discovery should come from build options.
 *
 * Build options are not exposed to services in the daemon, so this is a temporary solution to keep existing code working.
 *
 */
public class ProviderBackedToolchainConfiguration implements ToolchainConfiguration {
    private final ProviderFactory providerFactory;
    private final SystemProperties systemProperties;
    private final StartParameter startParameter;

    @Inject
    public ProviderBackedToolchainConfiguration(ProviderFactory providerFactory, StartParameter startParameter) {
        this(providerFactory, SystemProperties.getInstance(), startParameter);

    }

    ProviderBackedToolchainConfiguration(ProviderFactory providerFactory, SystemProperties systemProperties, StartParameter startParameter) {
        this.providerFactory = providerFactory;
        this.systemProperties = systemProperties;
        this.startParameter = startParameter;
    }

    /**
     * Retrieves a Gradle property, and emits a deprecation warning if it was specified as a project property.
     *
     * ToolchainBuildOptions takes care of capturing toolchain configuration system properties in the launcher and
     * shipping them to the daemon as project properties in {@link StartParameter}.  So, whether a property is set as
     * a system property or as a project property, it should still be available as a "Gradle property".  Here we check
     * if it was specified as a project property, but not a system property, and, if so, emit a deprecation warning.
     *
     * It's conceivable that it could be set as both a system property and a project property for migration purposes,
     * which is fine.  If so, we ensure that they have the same value, and if not, throw an exception.
     */
    private Provider<String> fromGradleProperty(String propertyName) {
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
                emitDeprecatedWarning(propertyName, startParameter.getProjectProperties().get(propertyName));
            }
        }

        return providerFactory.gradleProperty(propertyName);
    }

    private static void emitDeprecatedWarning(String propertyName, String value) {
        DeprecationLogger.deprecateAction("Specifying '" + propertyName + "' as a project property on the command line")
            .withAdvice("Instead, specify it as a Gradle property, i.e. `-D" + propertyName + "=" + value + "`.")
            .willBecomeAnErrorInGradle10()
            .withUpgradeGuideSection(9, "toolchain-project-properties")
            .nagUser();
    }

    @Override
    public Collection<String> getJavaInstallationsFromEnvironment() {
        return Arrays.asList(fromGradleProperty(EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setJavaInstallationsFromEnvironment(Collection<String> installations) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getInstallationsFromPaths() {
        return Arrays.asList(fromGradleProperty(LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setInstallationsFromPaths(Collection<String> installations) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAutoDetectEnabled() {
        return fromGradleProperty(ToolchainConfiguration.AUTO_DETECT).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
    }

    @Override
    public void setAutoDetectEnabled(boolean enabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDownloadEnabled() {
        return fromGradleProperty(AutoInstalledInstallationSupplier.AUTO_DOWNLOAD).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
    }

    @Override
    public void setDownloadEnabled(boolean enabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public File getAsdfDataDirectory() {
        String asdfEnvVar = providerFactory.environmentVariable("ASDF_DATA_DIR").getOrNull();
        if (asdfEnvVar != null) {
            return new File(asdfEnvVar);
        }
        return new File(systemProperties.getUserHome(), ".asdf");
    }

    @Override
    public File getIntelliJdkDirectory() {
        return fromGradleProperty("org.gradle.java.installations.idea-jdks-directory").map(File::new).getOrElse(defaultJdksDirectory(OperatingSystem.current()));
    }

    @Override
    public void setIntelliJdkDirectory(File intellijInstallationDirectory) {
        throw new UnsupportedOperationException();
    }

    private File defaultJdksDirectory(OperatingSystem os) {
        if (os.isMacOsX()) {
            return new File(systemProperties.getUserHome(), "Library/Java/JavaVirtualMachines");
        }
        return new File(systemProperties.getUserHome(), ".jdks");
    }

    @Override
    public File getJabbaHomeDirectory() {
        String jabbaHome = providerFactory.environmentVariable("JABBA_HOME").getOrNull();
        if (jabbaHome != null) {
            return new File(jabbaHome);
        }
        return null;
    }

    @Override
    public File getSdkmanCandidatesDirectory() {
        String asdfEnvVar = providerFactory.environmentVariable("SDKMAN_CANDIDATES_DIR").getOrNull();
        if (asdfEnvVar != null) {
            return new File(asdfEnvVar);
        }
        return new File(systemProperties.getUserHome(), ".sdkman/candidates");
    }

    @Override
    @Nullable
    public String getEnvironmentVariableValue(String variableName) {
        return providerFactory.environmentVariable(variableName).getOrNull();
    }
}
