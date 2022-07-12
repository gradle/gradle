/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.jvm.toolchain.internal.install;

import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaToolchainRepository;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.cache.FileLock;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.jvm.toolchain.internal.JavaToolchainRepositoryRegistryInternal;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, URI uri, @Nullable Throwable cause) {
            super("Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from: " + uri, cause);
        }

    }

    private final List<JavaToolchainRepository> toolchainRepositories;

    private final FileDownloader downloader;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    @Inject
    public DefaultJavaToolchainProvisioningService(
            JavaToolchainRepositoryRegistry toolchainRepositoryRegistry,
            FileDownloader downloader,
            JdkCacheDirectory cacheDirProvider,
            ProviderFactory factory,
            BuildOperationExecutor executor
    ) {
        toolchainRepositoryRegistry.register("adoptOpenJdk", AdoptOpenJdkRemoteBinary.class); //TODO: hack until this implementation is moved into a plug-in

        this.toolchainRepositories = ((JavaToolchainRepositoryRegistryInternal) toolchainRepositoryRegistry).requestedRepositories();
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
            Optional<URI> uri = toolchainRepository.toUri(spec);
            if (uri.isPresent()) {
                Optional<JavaToolchainRepository.Metadata> metadata = toolchainRepository.toMetadata(spec);
                if (!metadata.isPresent()) {
                    throw new RuntimeException("Invalid toolchain repository implementation, metadata not provided for URI: " + uri);
                }
                return provisionInstallation(spec, uri.get(), metadata.get());
            }
        } //TODO: write test to confirm this in-order loop processing

        return Optional.empty();
    }

    private Optional<File> provisionInstallation(JavaToolchainSpec spec, URI uri, JavaToolchainRepository.Metadata metadata) {
        synchronized (PROVISIONING_PROCESS_LOCK) {
            String destinationFilename = toArchiveFileName(metadata);
            File destinationFile = cacheDirProvider.getDownloadLocation(destinationFilename);
            final FileLock fileLock = cacheDirProvider.acquireWriteLock(destinationFile, "Downloading toolchain");
            try {
                String displayName = "Provisioning toolchain " + destinationFile.getName();
                return wrapInOperation(
                        displayName,
                    () -> provisionJdk(uri, destinationFile));
            } catch (Exception e) {
                throw new MissingToolchainException(spec, uri, e);
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

    public static String toArchiveFileName(JavaToolchainRepository.Metadata metadata) {
        return String.format("%s-%s-%s-%s-%s.%s", metadata.vendor(), metadata.languageLevel(), metadata.architecture(), metadata.implementation(), metadata.operatingSystem(), metadata.fileExtension());
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
