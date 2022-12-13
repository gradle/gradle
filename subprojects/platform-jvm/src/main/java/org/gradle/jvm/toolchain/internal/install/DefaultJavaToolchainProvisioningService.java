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
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.cache.FileLock;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainInstallation;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.InstallationLocation;
import org.gradle.jvm.toolchain.internal.JavaToolchainMatcher;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, URI uri, @Nullable Throwable cause) {
            super("Unable to download toolchain matching the requirements (" + spec.getDisplayName() + ") from '" + uri + "'.", cause);
        }

    }

    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    private final JavaToolchainResolverRegistryInternal toolchainResolverRegistry;
    private final SecureFileDownloader downloader;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private final BuildPlatform buildPlatform;
    private final JvmMetadataDetector detector;

    @Inject
    public DefaultJavaToolchainProvisioningService(
            JavaToolchainResolverRegistry toolchainResolverRegistry,
            SecureFileDownloader downloader,
            JdkCacheDirectory cacheDirProvider,
            ProviderFactory factory,
            BuildOperationExecutor executor,
            BuildPlatform buildPlatform,
            JvmMetadataDetector detector
    ) {
        this.toolchainResolverRegistry = (JavaToolchainResolverRegistryInternal) toolchainResolverRegistry;
        this.downloader = downloader;
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.buildOperationExecutor = executor;
        this.buildPlatform = buildPlatform;
        this.detector = detector;
    }

    @Override
    public Optional<File> tryProvision(JavaToolchainSpec spec) {
        DefaultJavaToolchainRequest request = new DefaultJavaToolchainRequest(spec, buildPlatform);
        List<? extends RealizedJavaToolchainRepository> repositories = toolchainResolverRegistry.requestedRepositories();

        for (RealizedJavaToolchainRepository repository : repositories) {
            Optional<JavaToolchainInstallation> installation = repository.getResolver().resolveLocal(request);
            if (installation.isPresent()) {
                return Optional.of(validateLocalInstallation(installation.get().getJavaHome().toFile(), spec));
            }
        }

        if (!isAutoDownloadEnabled()) {
            return Optional.empty();
        }

        for (RealizedJavaToolchainRepository repository : repositories) {
            Optional<JavaToolchainDownload> download = repository.getResolver().resolve(request);
            if (download.isPresent()) {
                Collection<Authentication> authentications = repository.getAuthentications(download.get().getUri());
                return Optional.of(provisionInstallation(spec, download.get().getUri(), authentications));
            }
        }

        return Optional.empty();
    }

    private File validateLocalInstallation(File javaHome, JavaToolchainSpec spec) {
        if (!javaHome.isDirectory()) {
            throw new InvalidUserCodeException("Toolchain installation JAVA_HOME must point to a directory: " + javaHome);
        }

        JvmInstallationMetadata metadata = detector.getMetadata(new InstallationLocation(javaHome, "provisioned toolchain"));
        if (!metadata.isValidInstallation()) {
            throw new GradleException("Provisioned toolchain '" + javaHome + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }

        if (!new JavaToolchainMatcher(spec).test(metadata)) {
            throw new GradleException("Toolchain provisioned from '" + javaHome + "' doesn't satisfy the specification: " + spec.getDisplayName() + ".");
        }

        return javaHome;
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
