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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EnvironmentVariableListInstallationSupplier implements InstallationSupplier {

    private static final String JAVA_INSTALLATIONS_FROM_ENV_PROPERTY = "org.gradle.java.installations.fromEnv";

    private final ProviderFactory factory;
    private final FileResolver fileResolver;

    @Inject
    public EnvironmentVariableListInstallationSupplier(ProviderFactory factory, FileResolver fileResolver) {
        this.factory = factory;
        this.fileResolver = fileResolver;
    }

    @Override
    public String getSourceName() {
        return "environment variables from gradle property '" + JAVA_INSTALLATIONS_FROM_ENV_PROPERTY + "'";
    }

    @Override
    public Set<InstallationLocation> get() {
        final Provider<String> property = factory.gradleProperty(JAVA_INSTALLATIONS_FROM_ENV_PROPERTY);
        if (property.isPresent()) {
            final String listOfEnvironmentVariables = property.get();
            return Arrays.stream(listOfEnvironmentVariables.split(","))
                .map(this::resolveEnvironmentVariable)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private Optional<InstallationLocation> resolveEnvironmentVariable(String environmentVariable) {
        final Provider<String> value = environmentVariableValue(environmentVariable);
        if (value.isPresent()) {
            final String path = value.get().trim();
            if (!path.isEmpty()) {
                return Optional.of(new InstallationLocation(fileResolver.resolve(path), "environment variable '" + environmentVariable + "'"));
            }
        }
        return Optional.empty();
    }

    private Provider<String> environmentVariableValue(String environmentVariable) {
        return factory.environmentVariable(environmentVariable.trim());
    }


}
