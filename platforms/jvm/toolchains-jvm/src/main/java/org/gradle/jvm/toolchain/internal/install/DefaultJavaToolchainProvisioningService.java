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
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationRunner;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainResolver;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository;
import org.gradle.jvm.toolchain.internal.ToolchainDownloadFailedException;
import org.gradle.jvm.toolchain.internal.install.exceptions.MissingToolchainException;
import org.gradle.platform.BuildPlatform;
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
    private final BuildPlatform buildPlatform;

    @Inject
    public DefaultJavaToolchainProvisioningService(
        JavaToolchainResolverRegistry toolchainResolverRegistry,
        SecureFileDownloader downloader,
        JdkCacheDirectory cacheDirProvider,
        ProviderFactory factory,
        BuildOperationRunner executor,
        BuildPlatform buildPlatform
    ) {
        this.toolchainResolverRegistry = (JavaToolchainResolverRegistryInternal) toolchainResolverRegistry;
        this.downloader = downloader;
        this.cacheDirProvider = (DefaultJdkCacheDirectory)cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.buildOperationRunner = executor;
        this.buildPlatform = buildPlatform;
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
            throw new ToolchainDownloadFailedException("No locally installed toolchains match and toolchain auto-provisioning is not enabled.",
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".");
        }

        List<? extends RealizedJavaToolchainRepository> repositories = toolchainResolverRegistry.requestedRepositories();
        if (repositories.isEmpty()) {
            throw new ToolchainDownloadFailedException("No locally installed toolchains match and toolchain download repositories have not been configured.",
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + ".");
        }

        ToolchainDownloadFailureTracker downloadFailureTracker = new ToolchainDownloadFailureTracker();
        File successfulProvisioning = null;
        for (RealizedJavaToolchainRepository repository : repositories) {
            JavaToolchainResolver resolver = repository.getResolver();
            Optional<JavaToolchainDownload> download;
            try {
                download = resolver.resolve(new DefaultJavaToolchainRequest(spec, buildPlatform));
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
            throw downloadFailureTracker.buildFailureException();
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
                throw new MissingToolchainException(spec, uri, e);
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

        public ToolchainDownloadFailedException buildFailureException() {
            String message = "No matching toolchain could be found in the locally installed toolchains or the configured toolchain download repositories." +
                (hasFailures() ? " " + failureMessage() : "");

            String[] resolutions = {
                "Learn more about toolchain auto-detection at " + Documentation.userManual("toolchains", "sec:auto_detection").getUrl() + ".",
                "Learn more about toolchain repositories at " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl() + "."
            };

            ToolchainDownloadFailedException exception = new ToolchainDownloadFailedException(message, resolutions);

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
