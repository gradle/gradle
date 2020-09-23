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

import org.gradle.api.Transformer;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.install.internal.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.install.internal.JavaToolchainProvisioningService;

import javax.inject.Inject;
import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

public class JavaToolchainQueryService {

    private final SharedJavaInstallationRegistry registry;
    private final JavaToolchainFactory toolchainFactory;
    private final JavaToolchainProvisioningService installService;
    private final Provider<Boolean> detectEnabled;
    private final Provider<Boolean> downloadEnabled;

    @Inject
    public JavaToolchainQueryService(SharedJavaInstallationRegistry registry, JavaToolchainFactory toolchainFactory, JavaToolchainProvisioningService provisioningService, ProviderFactory factory) {
        this.registry = registry;
        this.toolchainFactory = toolchainFactory;
        this.installService = provisioningService;
        detectEnabled = factory.gradleProperty(AutoDetectingInstallationSupplier.AUTO_DETECT).forUseAtConfigurationTime().map(Boolean::parseBoolean);
        downloadEnabled = factory.gradleProperty(DefaultJavaToolchainProvisioningService.AUTO_DOWNLOAD).forUseAtConfigurationTime().map(Boolean::parseBoolean);
    }

    <T> Provider<T> toolFor(JavaToolchainSpec spec, Transformer<T, JavaToolchain> toolFunction) {
        return findMatchingToolchain(spec).map(toolFunction);
    }

    Provider<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        return new DefaultProvider<>(() -> {
            if (((DefaultToolchainSpec) filter).isConfigured()) {
                return query(filter);
            } else {
                return null;
            }
        });
    }

    private JavaToolchain query(JavaToolchainSpec filter) {
        return registry.listInstallations().stream()
            .map(this::asToolchain)
            .filter(matchingToolchain(filter))
            .sorted(new JavaToolchainComparator())
            .findFirst()
            .orElseGet(() -> downloadToolchain(filter));
    }

    private JavaToolchain downloadToolchain(JavaToolchainSpec spec) {
        final Optional<File> installation = installService.tryInstall(spec);
        return installation.map(this::asToolchain).orElseThrow(() -> noToolchainAvailable(spec));
    }

    private NoToolchainAvailableException noToolchainAvailable(JavaToolchainSpec spec) {
        return new NoToolchainAvailableException(spec, detectEnabled.getOrElse(true), downloadEnabled.getOrElse(true));
    }

    private Predicate<JavaToolchain> matchingToolchain(JavaToolchainSpec spec) {
        return toolchain -> toolchain.getLanguageVersion().equals(spec.getLanguageVersion().get());
    }

    private JavaToolchain asToolchain(File javaHome) {
        return toolchainFactory.newInstance(javaHome);
    }
}
