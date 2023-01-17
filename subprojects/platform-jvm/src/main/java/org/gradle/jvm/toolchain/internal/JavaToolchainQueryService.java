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
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@ServiceScope(Scopes.Project.class)
public class JavaToolchainQueryService {

    private final JavaInstallationRegistry registry;
    private final JavaToolchainFactory toolchainFactory;
    private final JavaToolchainProvisioningService installService;
    private final Provider<Boolean> detectEnabled;
    private final Provider<Boolean> downloadEnabled;
    private final Map<JavaToolchainSpecInternal.Key, Object> matchingToolchains;

    @Inject
    public JavaToolchainQueryService(
        JavaInstallationRegistry registry,
        JavaToolchainFactory toolchainFactory,
        JavaToolchainProvisioningService provisioningService,
        ProviderFactory factory
    ) {
        this.registry = registry;
        this.toolchainFactory = toolchainFactory;
        this.installService = provisioningService;
        this.detectEnabled = factory.gradleProperty(AutoDetectingInstallationSupplier.AUTO_DETECT).map(Boolean::parseBoolean);
        this.downloadEnabled = factory.gradleProperty(DefaultJavaToolchainProvisioningService.AUTO_DOWNLOAD).map(Boolean::parseBoolean);
        this.matchingToolchains = new HashMap<>();
    }

    <T> Provider<T> toolFor(
        JavaToolchainSpec spec,
        Transformer<T, JavaToolchain> toolFunction,
        DefaultJavaToolchainUsageProgressDetails.JavaTool requestedTool
    ) {
        return findMatchingToolchain(spec)
            .withSideEffect(toolchain -> toolchain.emitUsageEvent(requestedTool))
            .map(toolFunction);
    }

    @VisibleForTesting
    ProviderInternal<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        JavaToolchainSpecInternal filterInternal = (JavaToolchainSpecInternal) Objects.requireNonNull(filter);

        return new DefaultProvider<>(() -> {
            if (!filterInternal.isValid()) {
                DeprecationLogger.deprecate("Using toolchain specifications without setting a language version")
                    .withAdvice("Consider configuring the language version.")
                    .willBecomeAnErrorInGradle8()
                    .withUpgradeGuideSection(7, "invalid_toolchain_specification_deprecation")
                    .nagUser();
            }

            if (!filterInternal.isConfigured()) {
                return null;
            }

            synchronized (matchingToolchains) {
                if (matchingToolchains.containsKey(filterInternal.toKey())) {
                    return handleMatchingToolchainCached(filterInternal);
                } else {
                    return handleMatchingToolchainUnknown(filterInternal);
                }
            }
        });
    }

    private JavaToolchain handleMatchingToolchainCached(JavaToolchainSpecInternal filterInternal) throws Exception {
        Object previousResult = matchingToolchains.get(filterInternal.toKey());
        if (previousResult instanceof Exception) {
            throw (Exception) previousResult;
        } else {
            return (JavaToolchain) previousResult;
        }
    }

    private JavaToolchain handleMatchingToolchainUnknown(JavaToolchainSpecInternal filterInternal) {
        try {
            JavaToolchain toolchain = query(filterInternal);
            matchingToolchains.put(filterInternal.toKey(), toolchain);
            return toolchain;
        } catch (Exception e) {
            matchingToolchains.put(filterInternal.toKey(), e);
            throw e;
        }
    }

    private JavaToolchain query(JavaToolchainSpec spec) {
        if (spec instanceof CurrentJvmToolchainSpec) {
            return asToolchain(new InstallationLocation(Jvm.current().getJavaHome(), "current JVM"), spec).get();
        }

        if (spec instanceof SpecificInstallationToolchainSpec) {
            return asToolchain(new InstallationLocation(((SpecificInstallationToolchainSpec) spec).getJavaHome(), "specific installation"), spec).get();
        }

        warnIfAutoProvisioningOnWithoutRepositoryDefinitions();

        Optional<JavaToolchain> detectedToolchain = registry.listInstallations().stream()
            .map(javaHome -> asToolchain(javaHome, spec))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .filter(new JavaToolchainMatcher(spec))
            .min(new JavaToolchainComparator());

        if (detectedToolchain.isPresent()) {
            return detectedToolchain.get();
        }

        InstallationLocation downloadedInstallation = downloadToolchain(spec);
        JavaToolchain downloadedToolchain = asToolchainOrThrow(downloadedInstallation, spec);
        registry.addInstallation(downloadedInstallation);
        return downloadedToolchain;
    }

    private void warnIfAutoProvisioningOnWithoutRepositoryDefinitions() {
        if (installService.isAutoDownloadEnabled() && !installService.hasConfiguredToolchainRepositories()) {
            DeprecationLogger.deprecateBehaviour("Java toolchain auto-provisioning enabled, but no java toolchain repositories declared by the build. Will rely on the built-in repository.")
                .withAdvice("In order to declare a repository for java toolchains, you must edit your settings script and add one via the toolchainManagement block.")
                .willBeRemovedInGradle8()
                .withUserManual("toolchains", "sec:provisioning")
                .nagUser();
        }
    }

    private InstallationLocation downloadToolchain(JavaToolchainSpec spec) {
        final Optional<File> installation = installService.tryInstall(spec);
        if (!installation.isPresent()) {
            throw new NoToolchainAvailableException(spec, detectEnabled.getOrElse(true), downloadEnabled.getOrElse(true));
        }

        return new InstallationLocation(installation.get(), "provisioned toolchain");
    }

    private JavaToolchain asToolchainOrThrow(InstallationLocation javaHome, JavaToolchainSpec spec) {
        Optional<JavaToolchain> toolchain = asToolchain(javaHome, spec);
        if (!toolchain.isPresent()) {
            throw new GradleException("Toolchain installation '" + javaHome.getLocation() + "' could not be probed.");
        }
        return toolchain.get();
    }

    private Optional<JavaToolchain> asToolchain(InstallationLocation javaHome, JavaToolchainSpec spec) {
        return toolchainFactory.newInstance(javaHome, new JavaToolchainInput(spec));
    }
}
