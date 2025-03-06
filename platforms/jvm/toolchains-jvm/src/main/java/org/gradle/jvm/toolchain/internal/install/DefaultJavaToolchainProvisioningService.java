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

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.cache.FileLock;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainResolver;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.platform.internal.CurrentBuildPlatform;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository;
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainDownloadException;
import org.gradle.jvm.toolchain.internal.install.exceptions.ToolchainProvisioningException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier.AUTO_DOWNLOAD;

public class DefaultJavaToolchainProvisioningService implements JavaToolchainProvisioningService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultJavaToolchainProvisioningService.class);
    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    private final JavaToolchainResolverRegistryInternal toolchainResolverRegistry;
    private final SecureFileDownloader downloader;
    private final DefaultJdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationRunner buildOperationRunner;
    private final CurrentBuildPlatform currentBuildPlatform;

    @Inject
    public DefaultJavaToolchainProvisioningService(
        JavaToolchainResolverRegistry toolchainResolverRegistry,
        SecureFileDownloader downloader,
        JdkCacheDirectory cacheDirProvider,
        ProviderFactory factory,
        BuildOperationRunner executor,
        CurrentBuildPlatform currentBuildPlatform
    ) {
        this.toolchainResolverRegistry = (JavaToolchainResolverRegistryInternal) toolchainResolverRegistry;
        this.downloader = downloader;
        this.cacheDirProvider = (DefaultJdkCacheDirectory)cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.buildOperationRunner = executor;
        this.currentBuildPlatform = currentBuildPlatform;
    }

    @Override
    public boolean isAutoDownloadEnabled() {
        return downloadEnabled.getOrElse(true);
    }

    @Override
    public boolean hasConfiguredToolchainRepositories() {
        return !toolchainResolverRegistry.requestedRepositories().isEmpty();
    }

    @Override
    public File tryInstall(JavaToolchainSpec spec) {
        if (!isAutoDownloadEnabled()) {
            throw new ToolchainProvisioningException(spec, "Toolchain auto-provisioning is not enabled.",
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION);
        }

        List<? extends RealizedJavaToolchainRepository> repositories = toolchainResolverRegistry.requestedRepositories();
        if (repositories.isEmpty()) {
            throw new ToolchainProvisioningException(spec, "Toolchain download repositories have not been configured.",
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION,
                ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION);
        }

        // TODO: This should be refactored to leverage the new JavaToolchainResolverService but the current error handling makes it hard
        // However, this exception handling is wrong as it may cause unreproducible behaviors since we can query a later resolver when a previous one fails.
        ToolchainDownloadFailureTracker downloadFailureTracker = new ToolchainDownloadFailureTracker();
        File successfulProvisioning = null;
        for (RealizedJavaToolchainRepository repository : repositories) {
            JavaToolchainResolver resolver = repository.getResolver();
            Optional<JavaToolchainDownload> download;
            try {
                download = resolver.resolve(new DefaultJavaToolchainRequest(spec, currentBuildPlatform.toBuildPlatform()));
            } catch (Exception e) {
                downloadFailureTracker.addResolveFailure(repository.getRepositoryName(), e);
                continue;
            }
            try {
                if (download.isPresent()) {
                    Collection<Authentication> authentications = repository.getAuthentications(download.get().getUri());
                    successfulProvisioning = provisionInstallation(spec, download.get().getUri(), authentications);
                    break;
                }
            } catch (Exception e) {
                downloadFailureTracker.addProvisioningFailure(repository.getRepositoryName(), e);
                // continue
            }
        }

        if (successfulProvisioning == null) {
            throw downloadFailureTracker.buildFailureException(spec);
        } else {
            downloadFailureTracker.logFailuresIfAny();
            return successfulProvisioning;
        }
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
                throw new ToolchainDownloadException(spec, uri, e);
            }
        }
    }

    private <T> T wrapInOperation(String displayName, Callable<T> provisioningStep) {
        return buildOperationRunner.call(new ToolchainProvisioningBuildOperation<>(displayName, provisioningStep));
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

    private static class ToolchainDownloadFailureTracker {

        private final Map<String, Exception> resolveFailures = new TreeMap<>();
        private final Map<String, Exception> provisioningFailures = new TreeMap<>();

        public void addResolveFailure(String repositoryName, Exception failure) {
            resolveFailures.put(repositoryName, failure);
        }

        public void addProvisioningFailure(String repositoryName, Exception failure) {
            provisioningFailures.put(repositoryName, failure);
        }

        public ToolchainProvisioningException buildFailureException(JavaToolchainSpec spec) {
            String cause = "No matching toolchain could be found in the configured toolchain download repositories.";
            if (hasFailures()) {
                cause = failureMessage();
            }

            String[] resolutions = {
                ToolchainProvisioningException.AUTO_DETECTION_RESOLUTION,
                ToolchainProvisioningException.DOWNLOAD_REPOSITORIES_RESOLUTION
            };
            ToolchainProvisioningException exception = new ToolchainProvisioningException(spec, cause, resolutions);

            return addFailuresAsSuppressed(exception);
        }

        private <T extends Exception> T addFailuresAsSuppressed(T exception) {
            for (Exception resolveFailure : resolveFailures.values()) {
                exception.addSuppressed(resolveFailure);
            }

            for (Exception provisionFailure : provisioningFailures.values()) {
                exception.addSuppressed(provisionFailure);
            }

            return exception;
        }

        public void logFailuresIfAny() {
            if (hasFailures()) {
                LOGGER.warn(failureMessage() + " Switch logging level to DEBUG (--debug) for further information.");
                if (LOGGER.isDebugEnabled()) {
                    String failureMessage = failureMessage();
                    LOGGER.debug(failureMessage, addFailuresAsSuppressed(new Exception(failureMessage)));
                }
            }
        }

        private boolean hasFailures() {
            return !resolveFailures.isEmpty() || !provisioningFailures.isEmpty();
        }

        private String failureMessage() {
            StringBuilder sb = new StringBuilder();
            if (!resolveFailures.isEmpty()) {
                sb.append("Some toolchain resolvers had internal failures: ")
                    .append(failureMessage(resolveFailures))
                    .append(".");
            }
            if (!provisioningFailures.isEmpty()) {
                sb.append(resolveFailures.isEmpty() ? "" : " ");
                sb.append("Some toolchain resolvers had provisioning failures: ")
                    .append(failureMessage(provisioningFailures))
                    .append(".");
            }
            return sb.toString();
        }

        private static String failureMessage(Map<String, Exception> failures) {
            return failures.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue().getMessage() + ")").collect(Collectors.joining(", "));
        }
    }
}
