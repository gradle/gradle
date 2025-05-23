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

import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;

import javax.annotation.Nullable;
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

    @Inject
    public ProviderBackedToolchainConfiguration(ProviderFactory providerFactory) {
        this(providerFactory, SystemProperties.getInstance());
    }

    ProviderBackedToolchainConfiguration(ProviderFactory providerFactory, SystemProperties systemProperties) {
        this.providerFactory = providerFactory;
        this.systemProperties = systemProperties;
    }

    @Override
    public Collection<String> getJavaInstallationsFromEnvironment() {
        return Arrays.asList(providerFactory.gradleProperty(EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setJavaInstallationsFromEnvironment(Collection<String> installations) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> getInstallationsFromPaths() {
        return Arrays.asList(providerFactory.gradleProperty(LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setInstallationsFromPaths(Collection<String> installations) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isAutoDetectEnabled() {
        return providerFactory.gradleProperty(ToolchainConfiguration.AUTO_DETECT).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
    }

    @Override
    public void setAutoDetectEnabled(boolean enabled) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDownloadEnabled() {
        return providerFactory.gradleProperty(AutoInstalledInstallationSupplier.AUTO_DOWNLOAD).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
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
        return providerFactory.gradleProperty("org.gradle.java.installations.idea-jdks-directory").map(File::new).getOrElse(defaultJdksDirectory(OperatingSystem.current()));
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
    public @Nullable String getEnvironmentVariableValue(String variableName) {
        return providerFactory.environmentVariable(variableName).getOrNull();
    }
}
