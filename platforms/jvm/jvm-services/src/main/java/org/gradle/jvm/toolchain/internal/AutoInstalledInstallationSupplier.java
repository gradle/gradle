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

package org.gradle.jvm.toolchain.internal;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class AutoInstalledInstallationSupplier implements InstallationSupplier {

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    private final ToolchainConfiguration configuration;
    private final JdkCacheDirectory cacheDirProvider;

    @Inject
    public AutoInstalledInstallationSupplier(ToolchainConfiguration configuration, JdkCacheDirectory cacheDirProvider) {
        this.configuration = configuration;
        this.cacheDirProvider = cacheDirProvider;
    }

    @Override
    public String getSourceName() {
        return "Auto-provisioned by Gradle";
    }

    @Override
    public Set<InstallationLocation> get() {
        if (configuration.isAutoDetectEnabled() || configuration.isDownloadEnabled()) {
            return cacheDirProvider.listJavaHomes().stream()
                .map(this::asInstallation)
                .collect(Collectors.toSet());
        } else {
            return Collections.emptySet();
        }
    }

    private InstallationLocation asInstallation(File javaHome) {
        return InstallationLocation.autoProvisioned(javaHome, getSourceName());
    }
}
