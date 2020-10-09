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

package org.gradle.jvm.toolchain.install.internal;

import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.FileLock;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Optional;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, @Nullable Throwable cause) {
            super("Unable to download toolchain matching these requirements: " + spec.getDisplayName(), cause);
        }

    }

    private final AdoptOpenJdkRemoteBinary openJdkBinary;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private static Object provisioningProcessLock = new Object();

    @Inject
    public DefaultJavaToolchainProvisioningService(AdoptOpenJdkRemoteBinary openJdkBinary, JdkCacheDirectory cacheDirProvider, ProviderFactory factory) {
        this.openJdkBinary = openJdkBinary;
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).forUseAtConfigurationTime().map(Boolean::parseBoolean);
    }

    public Optional<File> tryInstall(JavaToolchainSpec spec) {
        if (!isAutoDownloadEnabled()) {
            return Optional.empty();
        }
        synchronized (provisioningProcessLock) {
            String destinationFilename = openJdkBinary.toFilename(spec);
            File destinationFile = cacheDirProvider.getDownloadLocation(destinationFilename);
            final FileLock fileLock = cacheDirProvider.acquireWriteLock(destinationFile, "Downloading toolchain");
            try {
                return provisionJdk(spec, destinationFile);
            } catch (Exception e) {
                throw new MissingToolchainException(spec, e);
            } finally {
                fileLock.close();
            }
        }
    }

    private Optional<File> provisionJdk(JavaToolchainSpec spec, File destinationFile) {
        final Optional<File> jdkArchive;
        if (destinationFile.exists()) {
            jdkArchive = Optional.of(destinationFile);
        } else {
            jdkArchive = openJdkBinary.download(spec, destinationFile);
        }
        return jdkArchive.map(cacheDirProvider::provisionFromArchive);
    }

    private boolean isAutoDownloadEnabled() {
        return downloadEnabled.getOrElse(true);
    }

}
