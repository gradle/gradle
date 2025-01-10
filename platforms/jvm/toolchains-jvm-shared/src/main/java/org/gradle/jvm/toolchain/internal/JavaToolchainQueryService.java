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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.exceptions.NoToolchainAvailableException;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@ServiceScope({Scope.Global.class, Scope.Project.class})
public class JavaToolchainQueryService {

    private static final class ToolchainLookupKey {
        private final JavaToolchainSpecInternal.Key specKey;
        private final Set<JavaInstallationCapability> requiredCapabilities;

        private ToolchainLookupKey(JavaToolchainSpecInternal.Key specKey, Set<JavaInstallationCapability> requiredCapabilities) {
            this.specKey = specKey;
            this.requiredCapabilities = Sets.immutableEnumSet(requiredCapabilities);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ToolchainLookupKey that = (ToolchainLookupKey) o;
            return Objects.equals(specKey, that.specKey) && Objects.equals(requiredCapabilities, that.requiredCapabilities);
        }

        @Override
        public int hashCode() {
            return Objects.hash(specKey, requiredCapabilities);
        }

        @Override
        public String toString() {
            return "ToolchainLookupKey{" +
                "specKey=" + specKey +
                ", requiredCapabilities=" + requiredCapabilities +
                '}';
        }
    }

    // A key that matches only the fallback toolchain
    private static final JavaToolchainSpecInternal.Key FALLBACK_TOOLCHAIN_KEY = new JavaToolchainSpecInternal.Key() {
        @Override
        public String toString() {
            return "FallbackToolchainSpecKey";
        }
    };

    private final FileFactory fileFactory;
    private final JvmMetadataDetector detector;
    private final JavaToolchainProvisioningService installService;
    // Map values are either `JavaToolchain` or `Exception`
    private final ConcurrentMap<ToolchainLookupKey, Object> matchingToolchains;
    private final JavaToolchainSpec fallbackToolchainSpec;
    private final File currentJavaHome;
    private final JavaInstallationRegistry registry;

    @Inject
    public JavaToolchainQueryService(
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        JavaInstallationRegistry registry,
        JavaToolchainSpec fallbackToolchainSpec
    ) {
        this(detector, fileFactory, provisioningService, registry, fallbackToolchainSpec, Jvm.current().getJavaHome());
    }

    @VisibleForTesting
    JavaToolchainQueryService(
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        JavaInstallationRegistry registry,
        JavaToolchainSpec fallbackToolchainSpec,
        File currentJavaHome
    ) {
        this.detector = detector;
        this.fileFactory = fileFactory;
        this.installService = provisioningService;
        this.matchingToolchains = new ConcurrentHashMap<>();
        this.fallbackToolchainSpec = fallbackToolchainSpec;
        this.currentJavaHome = currentJavaHome;
        this.registry = registry;
    }

    public ProviderInternal<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        return findMatchingToolchain(filter, Collections.emptySet());
    }

    public ProviderInternal<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter, Set<JavaInstallationCapability> requiredCapabilities) {
        JavaToolchainSpecInternal filterInternal = (JavaToolchainSpecInternal) Objects.requireNonNull(filter);
        return new DefaultProvider<>(() -> resolveToolchain(filterInternal, requiredCapabilities));
    }

    private JavaToolchain resolveToolchain(JavaToolchainSpecInternal requestedSpec, Set<JavaInstallationCapability> requiredCapabilities) throws Exception {
        requestedSpec.finalizeProperties();

        if (!requestedSpec.isValid()) {
            throw DocumentedFailure.builder()
                .withSummary("Using toolchain specifications without setting a language version is not supported.")
                .withAdvice("Consider configuring the language version.")
                .withUpgradeGuideSection(7, "invalid_toolchain_specification_deprecation")
                .build();
        }

        boolean useFallback = !requestedSpec.isConfigured();
        JavaToolchainSpec actualSpec = useFallback ? fallbackToolchainSpec : requestedSpec;
        // We can't use the key of the fallback toolchain spec, because it is a spec that can match configured requests as well
        JavaToolchainSpecInternal.Key actualSpecKey = useFallback ? FALLBACK_TOOLCHAIN_KEY : requestedSpec.toKey();
        ToolchainLookupKey actualKey = new ToolchainLookupKey(actualSpecKey, requiredCapabilities);

        // TODO: We could optimize here by reusing results which have capabilities that are supersets of the required capabilities
        // Currently this issues a new query for each required capability set, which usually means at least 2 queries for a normal Java project (compiler + tests or application)
        Object resolutionResult = matchingToolchains.computeIfAbsent(actualKey, key -> {
            try {
                return query(actualSpec, requiredCapabilities, useFallback);
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

    private JavaToolchain query(JavaToolchainSpec spec, Set<JavaInstallationCapability> requiredCapabilities, boolean isFallback) {
        if (spec instanceof CurrentJvmToolchainSpec) {
            return asToolchainOrThrow(InstallationLocation.autoDetected(currentJavaHome, "current JVM"), spec, requiredCapabilities, isFallback);
        }

        if (spec instanceof SpecificInstallationToolchainSpec) {
            return asToolchainOrThrow(InstallationLocation.userDefined(((SpecificInstallationToolchainSpec) spec).getJavaHome(), "specific installation"), spec, requiredCapabilities, false);
        }

        return findInstalledToolchain(spec, requiredCapabilities).orElseGet(() -> downloadToolchain(spec, requiredCapabilities));
    }

    private Optional<JavaToolchain> findInstalledToolchain(JavaToolchainSpec spec, Set<JavaInstallationCapability> requiredCapabilities) {
        Predicate<JvmInstallationMetadata> matcher = new JvmInstallationMetadataMatcher(spec, requiredCapabilities);

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
                        "see " + Documentation.userManual("toolchains", "sub:download_repositories").getUrl()
                )
                .withUserManual("toolchains", "sub:download_repositories") //has no effect due to bug in DeprecationLogger.warnOfChangedBehaviour
                .nagUser();
        }
    }

    private JavaToolchain downloadToolchain(JavaToolchainSpec spec, Set<JavaInstallationCapability> requiredCapabilities) {
        File installation;
        try {
            // TODO: inform installation process of required capabilities, needs public API change
            installation = installService.tryInstall(spec);
        } catch (ToolchainDownloadFailedException e) {
            throw new NoToolchainAvailableException(spec, e);
        }

        InstallationLocation downloadedInstallation = InstallationLocation.autoProvisioned(installation, "provisioned toolchain");
        JavaToolchain downloadedToolchain = asToolchainOrThrow(downloadedInstallation, spec, requiredCapabilities, false);
        registry.addInstallation(downloadedInstallation);
        return downloadedToolchain;
    }

    private JavaToolchain asToolchainOrThrow(InstallationLocation javaHome, JavaToolchainSpec spec, Set<JavaInstallationCapability> requiredCapabilities, boolean isFallback) {
        final JvmInstallationMetadata metadata = detector.getMetadata(javaHome);

        if (!metadata.isValidInstallation()) {
            throw new GradleException("Toolchain installation '" + javaHome.getLocation() + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }
        if (!metadata.getCapabilities().containsAll(requiredCapabilities)) {
            throw new GradleException("Toolchain installation '" + javaHome.getLocation() + "' does not provide the required capabilities: " + requiredCapabilities);
        }
        return new JavaToolchain(metadata, fileFactory, new JavaToolchainInput(spec), isFallback);
    }
}
