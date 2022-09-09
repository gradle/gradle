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
import org.gradle.authentication.Authentication;
import org.gradle.cache.FileLock;
import org.gradle.env.BuildEnvironment;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.jvm.toolchain.JavaToolchainRepositoryRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainRepositoryRegistryInternal;
import org.gradle.jvm.toolchain.internal.JavaToolchainRepositoryRequest;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
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

    private final JavaToolchainRepositoryRegistryInternal toolchainRepositoryRegistry;
    private final AdoptOpenJdkRemoteBinary openJdkBinary;

    private final SecureFileDownloader downloader;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationExecutor buildOperationExecutor;

    private final BuildEnvironment buildEnvironment;

    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    @Inject
    public DefaultJavaToolchainProvisioningService(
            JavaToolchainRepositoryRegistry toolchainRepositoryRegistry,
            AdoptOpenJdkRemoteBinary openJdkBinary,
            SecureFileDownloader downloader,
            JdkCacheDirectory cacheDirProvider,
            ProviderFactory factory,
            BuildOperationExecutor executor,
            BuildEnvironment buildEnvironment
    ) {
        this.toolchainRepositoryRegistry = (JavaToolchainRepositoryRegistryInternal) toolchainRepositoryRegistry;
        this.openJdkBinary = openJdkBinary;
        this.downloader = downloader;
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.buildOperationExecutor = executor;
        this.buildEnvironment = buildEnvironment;
    }

    public Optional<File> tryInstall(JavaToolchainSpec spec) {
        if (!isAutoDownloadEnabled()) {
            return Optional.empty();
        }

        List<? extends JavaToolchainRepositoryRequest> requestedRepositories = toolchainRepositoryRegistry.requestedRepositories();

        if (requestedRepositories.isEmpty()) {
            DeprecationLogger.deprecateBehaviour("Java toolchain auto-provisioning needed, but no java toolchain repositories declared by the build. Will rely on the built-in repository.")
                    .withAdvice("In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block.")
                    .willBeRemovedInGradle8()
                    .withUserManual("toolchains", "sec:provisioning")
                    .nagUser();
            //TODO (#21082): write the removal PR asap, once all the other changes are in the master branch
            Optional<URI> uri = openJdkBinary.toUri(spec, buildEnvironment);
            if (uri.isPresent()) {
                return Optional.of(provisionInstallation(spec, uri.get(), Collections.emptyList()));
            }
        } else {
            for (JavaToolchainRepositoryRequest request : requestedRepositories) {
                Optional<URI> uri = request.getRepository().toUri(spec, buildEnvironment);
                if (uri.isPresent()) {
                    Collection<Authentication> authentications = request.getAuthentications(uri.get());
                    return Optional.of(provisionInstallation(spec, uri.get(), authentications));
                }
            }
        }

        return Optional.empty();
    }

    private File provisionInstallation(JavaToolchainSpec spec, URI uri, Collection<Authentication> authentications) {
        synchronized (PROVISIONING_PROCESS_LOCK) {
            try {
                File downloadFolder = cacheDirProvider.getDownloadLocation();
                ExternalResource resource = wrapInOperation("Examining toolchain URI " + uri, () -> downloader.getResourceFor(uri, authentications));
                File archiveFile = new File(downloadFolder, getFileName(uri, resource));
                final FileLock fileLock = cacheDirProvider.acquireWriteLock(archiveFile, "Downloading toolchain");
                try {
                    if (!archiveFile.exists()) {
                        wrapInOperation("Downloading toolchain from URI " + uri, () -> {
                            downloader.download(uri, archiveFile, resource);
                            return null;
                        });
                    }
                    return wrapInOperation("Unpacking toolchain archive " + archiveFile.getName(), () -> cacheDirProvider.provisionFromArchive(spec, archiveFile, uri));
                } finally {
                    fileLock.close();
                }
            } catch (Exception e) {
                throw new MissingToolchainException(spec, uri, e);
            }
        }
    }

    private String getFileName(URI uri, ExternalResource resource) {
        ExternalResourceMetaData metaData = resource.getMetaData();
        if (metaData == null) {
            throw ResourceExceptions.getMissing(uri);
        }
        String fileName = metaData.getFilename();
        if (fileName == null) {
            throw new GradleException("Can't determine filename for resource located at: " + uri);
        }
        return fileName;
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
