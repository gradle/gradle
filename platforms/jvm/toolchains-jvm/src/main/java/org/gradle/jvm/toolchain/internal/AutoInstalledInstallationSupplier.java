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

package org.gradle.jvm.toolchain.internal;

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;

import java.io.File;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoInstalledInstallationSupplier extends AutoDetectingInstallationSupplier {

    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;

    public AutoInstalledInstallationSupplier(ProviderFactory factory, JdkCacheDirectory cacheDirProvider) {
        super(factory);
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(DefaultJavaToolchainProvisioningService.AUTO_DOWNLOAD).map(Boolean::parseBoolean);
    }

    @Override
    public String getSourceName() {
        return "Auto-provisioned by Gradle";
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        return cacheDirProvider.listJavaHomes().stream()
            .map(this::asInstallation)
            .collect(Collectors.toSet());
    }

    private InstallationLocation asInstallation(File javaHome) {
        return new InstallationLocation(javaHome, getSourceName(), true);
    }

    @Override
    protected boolean isAutoDetectionEnabled() {
        return super.isAutoDetectionEnabled() || downloadEnabled.getOrElse(true);
    }

}
