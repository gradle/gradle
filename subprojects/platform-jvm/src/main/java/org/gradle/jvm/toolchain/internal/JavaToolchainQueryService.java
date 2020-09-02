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

import org.gradle.api.Action;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaCompiler;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JavadocTool;
import org.gradle.jvm.toolchain.install.internal.JavaToolchainProvisioningService;

import javax.inject.Inject;
import java.io.File;
import java.util.Optional;
import java.util.function.Predicate;

public class JavaToolchainQueryService {

    private final SharedJavaInstallationRegistry registry;
    private final JavaToolchainFactory toolchainFactory;
    private final JavaToolchainProvisioningService installService;
    private final ObjectFactory objectFactory;

    @Inject
    public JavaToolchainQueryService(SharedJavaInstallationRegistry registry, JavaToolchainFactory toolchainFactory, JavaToolchainProvisioningService provisioningService, ObjectFactory objectFactory) {
        this.registry = registry;
        this.toolchainFactory = toolchainFactory;
        this.installService = provisioningService;
        this.objectFactory = objectFactory;
    }

    Provider<JavaCompiler> compilerFrom(Action<? super JavaToolchainSpec> config) {
        return findMatchingToolchain(configureToolchainSpec(config)).map(JavaToolchain::getJavaCompiler);
    }

    Provider<JavaLauncher> launcherFrom(Action<? super JavaToolchainSpec> config) {
        return findMatchingToolchain(configureToolchainSpec(config)).map(JavaToolchain::getJavaLauncher);
    }

    Provider<JavadocTool> javadocToolFrom(Action<? super JavaToolchainSpec> config) {
        return findMatchingToolchain(configureToolchainSpec(config)).map(JavaToolchain::getJavadocTool);
    }

    public Provider<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        if (!((DefaultToolchainSpec) filter).isConfigured()) {
            return Providers.notDefined();
        }
        return new DefaultProvider<>(() -> query(filter));
    }

    private DefaultToolchainSpec configureToolchainSpec(Action<? super JavaToolchainSpec> config) {
        DefaultToolchainSpec toolchainSpec = objectFactory.newInstance(DefaultToolchainSpec.class);
        config.execute(toolchainSpec);
        return toolchainSpec;
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
        return installation.map(this::asToolchain).orElseThrow(() ->
            new NoToolchainAvailableException(spec));
    }

    private Predicate<JavaToolchain> matchingToolchain(JavaToolchainSpec spec) {
        return toolchain -> toolchain.getJavaMajorVersion() == spec.getLanguageVersion().get();
    }

    private JavaToolchain asToolchain(File javaHome) {
        return toolchainFactory.newInstance(javaHome);
    }
}
