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

import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Provider;

import javax.inject.Inject;
import java.io.File;
import java.util.function.Predicate;

public class JavaToolchainQueryService {

    private final SharedJavaInstallationRegistry registry;
    private final JavaToolchainFactory toolchainFactory;

    @Inject
    public JavaToolchainQueryService(SharedJavaInstallationRegistry registry, JavaToolchainFactory toolchainFactory) {
        this.registry = registry;
        this.toolchainFactory = toolchainFactory;
    }

    public Provider<JavaToolchain> findMatchingToolchain(JavaToolchainSpec filter) {
        return new DefaultProvider<>(() -> query(filter));
    }

    private JavaToolchain query(JavaToolchainSpec filter) {
        return registry.listInstallations().stream()
            .map(this::asToolchain)
            .filter(matchingToolchain(filter))
            .findFirst()
            .orElseThrow(() -> new NoToolchainAvailableException());
    }

    // TODO: to be replaced with AttributeContainer/AttributeMatcher
    private Predicate<JavaToolchain> matchingToolchain(JavaToolchainSpec spec) {
        return toolchain -> toolchain.getJavaMajorVersion() == spec.getLanguageVersion();
    }

    private JavaToolchain asToolchain(File javaHome) {
        return toolchainFactory.newInstance(javaHome);
    }

}
