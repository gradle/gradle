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

import com.google.common.annotations.VisibleForTesting;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.FileLock;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.JavaToolchainCandidate;
import org.gradle.jvm.toolchain.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

public class DefaultJavaToolchainProvisioningService implements org.gradle.jvm.toolchain.install.internal.JavaToolchainProvisioningService {
    private static final Comparator<JavaToolchainCandidate> BY_LANGUAGE_VERSION = Comparator.comparingInt(
        cand -> cand.getLanguageVersion().asInt()
    );

    public static final String AUTO_DOWNLOAD = "org.gradle.java.installations.auto-download";

    @Contextual
    private static class MissingToolchainException extends GradleException {

        public MissingToolchainException(JavaToolchainSpec spec, @Nullable Throwable cause) {
            super("Unable to download toolchain matching these requirements: " + spec.getDisplayName(), cause);
        }

    }

    private final JavaToolchainProvisioningService javaToolchainProvisioner;
    private final JdkCacheDirectory cacheDirProvider;
    private final Provider<Boolean> downloadEnabled;
    private final BuildOperationExecutor buildOperationExecutor;
    private final JavaToolChainProvisioningDetailsFactory detailsFactory;
    private static final Object PROVISIONING_PROCESS_LOCK = new Object();

    @Inject
    public DefaultJavaToolchainProvisioningService(
        AdoptOpenJdkRemoteProvisioningService javaToolchainProvisioner,
        JdkCacheDirectory cacheDirProvider,
        ProviderFactory factory,
        BuildOperationExecutor executor,
        SystemInfo systemInfo,
        OperatingSystem operatingSystem
    ) {
        this.javaToolchainProvisioner = javaToolchainProvisioner;
        this.cacheDirProvider = cacheDirProvider;
        this.downloadEnabled = factory.gradleProperty(AUTO_DOWNLOAD).forUseAtConfigurationTime().map(Boolean::parseBoolean);
        this.buildOperationExecutor = executor;
        this.detailsFactory = new JavaToolChainProvisioningDetailsFactory(systemInfo, operatingSystem);
    }

    private Optional<JavaToolchainCandidate> findCandidate(
        JavaToolchainProvisioningDetailsInternal details,
        JavaToolchainProvisioningService provisioner
    ) {
        provisioner.findCandidates(details);
        return details.getCandidates()
            .flatMap(candidates -> candidates.stream().max(BY_LANGUAGE_VERSION));
    }

    public Optional<File> tryInstall(JavaToolchainSpec spec) {
        JavaToolchainProvisioningDetailsInternal details = detailsFactory.newDetails(spec);
        Optional<JavaToolchainCandidate> candidate;
        if (!isAutoDownloadEnabled() || !(candidate = findCandidate(details, javaToolchainProvisioner)).isPresent()) {
            return Optional.empty();
        }
        return provisionInstallation(details, candidate.get());
    }

    private Optional<File> provisionInstallation(
        JavaToolchainProvisioningDetailsInternal details,
        JavaToolchainCandidate candidate
    ) {
        synchronized (PROVISIONING_PROCESS_LOCK) {
            JavaToolchainProvisioningService.LazyProvisioner lazyProvisioner = javaToolchainProvisioner.provisionerFor(candidate);
            File destinationFile = cacheDirProvider.getDownloadLocation(lazyProvisioner.getFileName());
            final FileLock fileLock = cacheDirProvider.acquireWriteLock(destinationFile, "Downloading toolchain");
            try {
                return wrapInOperation(
                    "Provisioning toolchain " + destinationFile.getName(),
                    () -> provisionCandidate(lazyProvisioner, destinationFile));
            } catch (Exception e) {
                throw new MissingToolchainException(details.getRequested(), e);
            } finally {
                fileLock.close();
            }
        }
    }

    private Optional<File> provisionCandidate(JavaToolchainProvisioningService.LazyProvisioner candidate, File destinationFile) {
        final Optional<File> jdkArchive;
        if (destinationFile.exists()) {
            jdkArchive = Optional.of(destinationFile);
        } else {
            jdkArchive = candidate.provision(destinationFile) ? Optional.of(destinationFile) : Optional.empty();
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

    @VisibleForTesting
    public static String determineArch(SystemInfo info) {
        switch (info.getArchitecture()) {
            case i386:
                return "x32";
            case amd64:
                return "x64";
            case aarch64:
                return "aarch64";
        }
        return info.getArchitectureName();
    }

    @VisibleForTesting
    public static String determineOs(OperatingSystem operatingSystem) {
        if (operatingSystem.isWindows()) {
            return "windows";
        } else if (operatingSystem.isMacOsX()) {
            return "mac";
        } else if (operatingSystem.isLinux()) {
            return "linux";
        }
        return operatingSystem.getFamilyName();
    }


    private static class JavaToolChainProvisioningDetailsFactory {
        private final String os;
        private final String arch;

        private JavaToolChainProvisioningDetailsFactory(SystemInfo systemInfo, OperatingSystem operatingSystem) {
            this.os = determineArch(systemInfo);
            this.arch = determineOs(operatingSystem);
        }

        public JavaToolchainProvisioningDetailsInternal newDetails(JavaToolchainSpec spec) {
            return new DefaultProvisioningDetails(spec, os, arch);
        }

        private class DefaultProvisioningDetails implements JavaToolchainProvisioningDetailsInternal {
            private final JavaToolchainSpec spec;
            private List<JavaToolchainCandidate> candidates;
            private final String defaultOs;
            private final String defaultArch;

            private DefaultProvisioningDetails(JavaToolchainSpec spec, String defaultOs, String defaultArch) {
                this.spec = spec;
                this.defaultOs = defaultOs;
                this.defaultArch = defaultArch;
            }

            @Override
            public JavaToolchainSpec getRequested() {
                return spec;
            }

            @Override
            public JavaToolchainCandidate.Builder newCandidate() {
                return new DefaultJavaToolchainCandidateBuilder(defaultOs, defaultArch);
            }

            @Override
            public void listCandidates(List<JavaToolchainCandidate> candidates) {
                this.candidates = candidates;
            }

            @Override
            public String getOperatingSystem() {
                return os;
            }

            @Override
            public String getSystemArch() {
                return arch;
            }

            @Override
            public Optional<List<JavaToolchainCandidate>> getCandidates() {
                return Optional.ofNullable(candidates);
            }
        }
    }


}
