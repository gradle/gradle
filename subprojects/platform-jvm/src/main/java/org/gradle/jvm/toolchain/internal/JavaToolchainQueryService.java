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
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.deprecation.DocumentedFailure;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadataComparator;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.operations.BuildOperationProgressEventEmitter;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;

import javax.inject.Inject;
import java.io.File;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

@ServiceScope(Scopes.Project.class) //TODO: should be much higher scoped, as many other toolchain related services, but is bogged down by the scope of services it depends on
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
    private final BuildOperationProgressEventEmitter eventEmitter;
    private final Jvm currentJvm;

    @Inject
    public JavaToolchainQueryService(
        JavaInstallationRegistry registry,
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        ObjectFactory objectFactory,
        BuildOperationProgressEventEmitter eventEmitter
    ) {
        this(registry, detector, fileFactory, provisioningService, objectFactory, eventEmitter, Jvm.current());
    }

    @VisibleForTesting
    JavaToolchainQueryService(
        JavaInstallationRegistry registry,
        JvmMetadataDetector detector,
        FileFactory fileFactory,
        JavaToolchainProvisioningService provisioningService,
        ObjectFactory objectFactory,
        BuildOperationProgressEventEmitter eventEmitter,
        Jvm currentJvm
    ) {
        this.registry = registry;
        this.detector = detector;
        this.fileFactory = fileFactory;
        this.installService = provisioningService;
        this.matchingToolchains = new ConcurrentHashMap<>();
        this.fallbackToolchainSpec = objectFactory.newInstance(CurrentJvmToolchainSpec.class);
        this.eventEmitter = eventEmitter;
        this.currentJvm = currentJvm;
    }

    <T> Provider<T> toolFor(
        JavaToolchainSpec spec,
        Transformer<T, JavaToolchain> toolFunction,
        DefaultJavaToolchainUsageProgressDetails.JavaTool requestedTool
    ) {
        return findMatchingToolchain(spec)
            .withSideEffect(toolchain -> eventEmitter.emitNowForCurrent(new DefaultJavaToolchainUsageProgressDetails(requestedTool, toolchain.getMetadata())))
            .map(toolFunction);
    }

    @VisibleForTesting
    ProviderInternal<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
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
            return asToolchainOrThrow(new InstallationLocation(currentJvm.getJavaHome(), "current JVM"), spec, isFallback);
        }

        if (spec instanceof SpecificInstallationToolchainSpec) {
            return asToolchainOrThrow(new InstallationLocation(((SpecificInstallationToolchainSpec) spec).getJavaHome(), "specific installation"), spec, false);
        }

        Predicate<JvmInstallationMetadata> matcher = new JvmInstallationMetadataMatcher(spec);

        Optional<JavaToolchainInstantiationResult> result = registry.listInstallations().stream()
            .map(location -> new JavaToolchainInstantiationResult(location, detector.getMetadata(location)))
            .filter(it -> it.metadata.isValidInstallation())
            .filter(it -> matcher.test(it.metadata))
            .min(Comparator.comparing(it -> it.metadata, new JvmInstallationMetadataComparator(currentJvm)));

        if (result.isPresent()) {
            JavaToolchainInstantiationResult pair = result.get();
            warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(pair.javaHome);
            return new JavaToolchain(pair.metadata, fileFactory, new JavaToolchainInput(spec), false);
        }

        InstallationLocation downloadedInstallation = downloadToolchain(spec);
        JavaToolchain downloadedToolchain = asToolchainOrThrow(downloadedInstallation, spec, false);
        registry.addInstallation(downloadedInstallation);
        return downloadedToolchain;
    }

    private void warnIfAutoProvisionedToolchainUsedWithoutRepositoryDefinitions(InstallationLocation javaHome) {
        boolean autoDetectedToolchain = javaHome.isAutoProvisioned();
        if (autoDetectedToolchain && installService.isAutoDownloadEnabled() && !installService.hasConfiguredToolchainRepositories()) {
            DeprecationLogger.warnOfChangedBehaviour(
                "Using a toolchain installed via auto-provisioning, but having no toolchain repositories configured",
                "Consider defining toolchain download repositories, otherwise the build might fail in clean environments; " +
                "see " + Documentation.userManual("toolchains", "sub:download_repositories").documentationUrl()
            )
            .withUserManual("toolchains", "sub:download_repositories") //has no effect due to bug in DeprecationLogger.warnOfChangedBehaviour
            .nagUser();
        }
    }

    private InstallationLocation downloadToolchain(JavaToolchainSpec spec) {
        try {
            File installation = installService.tryInstall(spec);
            return new InstallationLocation(installation, "provisioned toolchain", true);
        } catch (ToolchainDownloadFailedException e) {
            throw new NoToolchainAvailableException(spec, e);
        }
    }

    private JavaToolchain asToolchainOrThrow(InstallationLocation javaHome, JavaToolchainSpec spec, boolean isFallback) {
        final JvmInstallationMetadata metadata = detector.getMetadata(javaHome);

        if (metadata.isValidInstallation()) {
            return new JavaToolchain(metadata, fileFactory, new JavaToolchainInput(spec), isFallback);
        } else {
            throw new GradleException("Toolchain installation '" + javaHome.getLocation() + "' could not be probed: " + metadata.getErrorMessage(), metadata.getErrorCause());
        }
    }

    public class JavaToolchainInstantiationResult {

        private final InstallationLocation javaHome;
        private final JvmInstallationMetadata metadata;

        public JavaToolchainInstantiationResult(InstallationLocation javaHome, JvmInstallationMetadata metadata) {
            this.javaHome = javaHome;
            this.metadata = metadata;
        }
    }

}
