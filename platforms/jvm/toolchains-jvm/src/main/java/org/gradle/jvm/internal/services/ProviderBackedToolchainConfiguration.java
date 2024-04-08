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
import org.gradle.jvm.toolchain.internal.AutoDetectingInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collection;

public class ProviderBackedToolchainConfiguration implements ToolchainConfiguration {
    private final ProviderFactory providerFactory;

    @Inject
    public ProviderBackedToolchainConfiguration(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public Collection<String> getJavaInstallationsFromEnvironment() {
        return Arrays.asList(providerFactory.gradleProperty(EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setJavaInstallationsFromEnvironment(Collection<String> installations) {

    }

    @Override
    public Collection<String> getInstallationsFromPaths() {
        return Arrays.asList(providerFactory.gradleProperty(LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY).getOrElse("").split(","));
    }

    @Override
    public void setInstallationsFromPaths(Collection<String> installations) {

    }

    @Override
    public boolean isAutoDetectEnabled() {
        return providerFactory.gradleProperty(AutoDetectingInstallationSupplier.AUTO_DETECT).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
    }

    @Override
    public void setAutoDetectEnabled(boolean enabled) {

    }

    @Override
    public boolean isDownloadEnabled() {
        return providerFactory.gradleProperty(AutoInstalledInstallationSupplier.AUTO_DOWNLOAD).map(Boolean::parseBoolean).getOrElse(Boolean.TRUE);
    }

    @Override
    public void setDownloadEnabled(boolean enabled) {

    }
}
