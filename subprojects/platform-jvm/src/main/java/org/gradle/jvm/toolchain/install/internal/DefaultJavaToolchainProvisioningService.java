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
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, @Nullable Throwable cause) {
            super("Unable to download toolchain matching these requirements: " + spec.getDisplayName(), cause);
        }

    }

    private final List<JavaToolchainRepository> toolchainRepositories;

    private final FileDownloader downloader;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    @Inject
    public DefaultJavaToolchainProvisioningService(AdoptOpenJdkRemoteBinary openJdkBinary, FileDownloader downloader, JdkCacheDirectory cacheDirProvider, ProviderFactory factory, BuildOperationExecutor executor) {
        this.toolchainRepositories = Collections.singletonList(openJdkBinary); //TODO: the list of toolchain repositories should be passed in here
        this.downloader = downloader;
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.buildOperationExecutor = executor;
    }

    public Optional<File> tryInstall(JavaToolchainSpec spec) {
        if (!isAutoDownloadEnabled()) {
            return Optional.empty();
        }

        for (JavaToolchainRepository toolchainRepository : toolchainRepositories) {
            if (toolchainRepository.canProvide(spec)) {
                return provisionInstallation(spec, toolchainRepository);
            }
        } //TODO: write test to confirm this in-order loop processing

        return Optional.empty();
    }

    private Optional<File> provisionInstallation(JavaToolchainSpec spec, JavaToolchainRepository toolchainRepository) {
        synchronized (PROVISIONING_PROCESS_LOCK) {
            String destinationFilename = toolchainRepository.toArchiveFileName(spec);
            File destinationFile = cacheDirProvider.getDownloadLocation(destinationFilename);
            final FileLock fileLock = cacheDirProvider.acquireWriteLock(destinationFile, "Downloading toolchain");
            try {
                String displayName = "Provisioning toolchain " + destinationFile.getName();
                return wrapInOperation(
                        displayName,
                    () -> provisionJdk(toolchainRepository.toUri(spec), destinationFile));
            } catch (Exception e) {
                throw new MissingToolchainException(spec, e);
            } finally {
                fileLock.close();
            }
        }
    }

    private Optional<File> provisionJdk(URI source, File destination) {
        final Optional<File> jdkArchive;
        if (destination.exists()) {
            jdkArchive = Optional.of(destination);
        } else {
            downloader.download(source, destination);
            jdkArchive = Optional.of(destination);
        }
        return wrapInOperation("Unpacking toolchain archive", () -> jdkArchive.map(cacheDirProvider::provisionFromArchive));
    }

    private boolean isAutoDownloadEnabled() {
        return downloadEnabled.getOrElse(true);
    }

    private <T> T wrapInOperation(String displayName, Callable<T> provisioningStep) {
        return buildOperationExecutor.call(new ToolchainProvisioningBuildOperation<>(displayName, provisioningStep));
    }

    private static class ToolchainProvisioningBuildOperation<T> implements CallableBuildOperation<T> {
        private final String displayName;
        private final Callable<T> provisioningStep;

        public ToolchainProvisioningBuildOperation(String displayName, Callable<T> provisioningStep) {
            this.displayName = displayName;
            this.provisioningStep = provisioningStep;
        }

        @Override
        public T call(BuildOperationContext context) throws Exception {
            return provisioningStep.call();
        }

        @Override
        public BuildOperationDescriptor.Builder description() {
            return BuildOperationDescriptor
                .displayName(displayName)
                .progressDisplayName(displayName);
        }
    }
}
