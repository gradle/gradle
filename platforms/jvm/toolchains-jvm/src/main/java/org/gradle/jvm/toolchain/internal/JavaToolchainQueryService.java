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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.GradleException;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.platform.BuildPlatform;

import javax.inject.Inject;
import java.io.File;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@ServiceScope(Scopes.Project.class) //TODO #24353: should be much higher scoped, as many other toolchain related services, but is bogged down by the scope of services it depends on
public class JavaToolchainQueryService {

    // A key that matches only the fallback toolchain
    private static final JavaToolchainSpecInternal.Key FALLBACK_TOOLCHAIN_KEY = new JavaToolchainSpecInternal.Key() {
        @Override
        public String toString() {
            return "FallbackToolchainSpecKey";
        }
    };

    private final JavaInstallationRegistry registry;
    private final FileFactory fileFactory;
    private final JvmMetadataDetector detector;
    private final JavaToolchainProvisioningService installService;
    // Map values are either `JavaToolchain` or `Exception`
    private final ConcurrentMap<JavaToolchainSpecInternal.Key, Object> matchingToolchains;
    private final CurrentJvmToolchainSpec fallbackToolchainSpec;
    private final File currentJavaHome;
    private final BuildPlatform buildPlatform;

    @Inject
    public JavaToolchainQueryService(
        JavaInstallationRegistry registry,
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        ObjectFactory objectFactory,
        BuildPlatform buildPlatform
    ) {
        this(registry, detector, fileFactory, provisioningService, objectFactory, Jvm.current().getJavaHome(), buildPlatform);
    }

    @VisibleForTesting
    JavaToolchainQueryService(
        JavaInstallationRegistry registry,
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        ObjectFactory objectFactory,
        File currentJavaHome,
        BuildPlatform buildPlatform
    ) {
        this.registry = registry;
        this.detector = detector;
        this.fileFactory = fileFactory;
        this.installService = provisioningService;
        this.matchingToolchains = new ConcurrentHashMap<>();
        this.fallbackToolchainSpec = objectFactory.newInstance(CurrentJvmToolchainSpec.class);
        this.currentJavaHome = currentJavaHome;
        this.buildPlatform = buildPlatform;
    }

    public ProviderInternal<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        JavaToolchainSpecInternal filterInternal = (JavaToolchainSpecInternal) Objects.requireNonNull(filter);
        return new DefaultProvider<>(() -> resolveToolchain(filterInternal));
    }

    private JavaToolchain resolveToolchain(JavaToolchainSpecInternal requestedSpec) throws Exception {
        requestedSpec.finalizeProperties();

        if (!requestedSpec.isValid()) {
            throw DocumentedFailure.builder()
                .withSummary("Using toolchain specifications without setting a language version is not supported.")
                .withAdvice("Consider configuring the language version.")
                .withUpgradeGuideSection(7, "invalid_toolchain_specification_deprecation")
                .build();
        }

        boolean useFallback = !requestedSpec.isConfigured();
        JavaToolchainSpecInternal actualSpec = useFallback ? fallbackToolchainSpec : requestedSpec;
        // We can't use the key of the fallback toolchain spec, because it is a spec that can match configured requests as well
        JavaToolchainSpecInternal.Key actualKey = useFallback ? FALLBACK_TOOLCHAIN_KEY : requestedSpec.toKey();

        Object resolutionResult = matchingToolchains.computeIfAbsent(actualKey, key -> {
            try {
                return query(actualSpec, useFallback);
            } catch (Exception e) {
                return e;
            }
        });

        if (resolutionResult instanceof Exception) {
            throw (Exception) resolutionResult;
        } else {
            return (JavaToolchain) resolutionResult;
        }
    }

    private JavaToolchain query(JavaToolchainSpec spec, boolean isFallback) {
        if (spec instanceof CurrentJvmToolchainSpec) {
            return asToolchainOrThrow(new InstallationLocation(currentJavaHome, "current JVM"), spec, isFallback);
        }

        if (spec instanceof SpecificInstallationToolchainSpec) {
            return asToolchainOrThrow(new InstallationLocation(((SpecificInstallationToolchainSpec) spec).getJavaHome(), "specific installation"), spec, false);
        }

        return findInstalledToolchain(spec).orElseGet(() -> downloadToolchain(spec));
    }

    private Optional<JavaToolchain> findInstalledToolchain(JavaToolchainSpec spec) {
        Predicate<JvmInstallationMetadata> matcher = new JvmInstallationMetadataMatcher(spec);

        return registry.toolchains().stream()
            .filter(result -> result.metadata.isValidInstallation())
            .filter(result -> matcher.test(result.metadata))
            .min(Comparator.comparing(result -> result.metadata, new JvmInstallationMetadataComparator(currentJavaHome)))
            .map(result -> {
                warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(result.location);
                return new JavaToolchain(result.metadata, fileFactory, new JavaToolchainInput(spec), false);
            });
    }

    private void warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(InstallationLocation javaHome) {
        boolean autoDetectedToolchain = javaHome.isAutoProvisioned();
        if (autoDetectedToolchain && installService.isAutoDownloadEnabled() && !installService.hasConfiguredToolchainRepositories()) {
            DeprecationLogger.warnOfChangedBehaviour(
                    "Using a toolchain installed via auto-provisioning, but having no toolchain repositories configured",
                    "Consider defining toolchain download repositories, otherwise the build might fail in clean environments; " +
                        "see " + Documentation.userManual("toolchains", "sub:download_repositories").url()
                )
                .withUserManual("toolchains", "sub:download_repositories") //has no effect due to bug in DeprecationLogger.warnOfChangedBehaviour
                .nagUser();
        }
    }

    private JavaToolchain downloadToolchain(JavaToolchainSpec spec) {
        File installation;
        try {
            installation = installService.tryInstall(spec);
        } catch (ToolchainDownloadFailedException e) {
            throw new NoToolchainAvailableException(spec, buildPlatform, e);
        }

        InstallationLocation downloadedInstallation = new InstallationLocation(installation, "provisioned toolchain", true);
        JavaToolchain downloadedToolchain = asToolchainOrThrow(downloadedInstallation, spec, false);
        registry.addInstallation(downloadedInstallation);
        return downloadedToolchain;
    }

    private JavaToolchain asToolchainOrThrow(InstallationLocation javaHome, JavaToolchainSpec spec, boolean isFallback) {
        final JvmInstallationMetadata metadata = detector.getMetadata(javaHome);

        if (metadata.isValidInstallation()) {
            return new JavaToolchain(metadata, fileFactory, new JavaToolchainInput(spec), isFallback);
        } else {
            throw new GradleException("Toolchain installation '" + javaHome.getLocation() + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }
    }
}
