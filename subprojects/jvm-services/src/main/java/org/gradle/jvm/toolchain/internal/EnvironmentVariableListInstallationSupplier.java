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

import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EnvironmentVariableListInstallationSupplier implements InstallationSupplier {

    private final ProviderFactory factory;

    @Inject
    public EnvironmentVariableListInstallationSupplier(ProviderFactory factory) {
        this.factory = factory;
    }

    @Override
    public Set<InstallationLocation> get() {
        final Provider<String> property = factory.gradleProperty("org.gradle.java.installations.fromEnv").forUseAtConfigurationTime();
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
                return Optional.of(new InstallationLocation(new File(path), "environment variable '" + environmentVariable + "'"));
            }
        }
        return Optional.empty();
    }

    private Provider<String> environmentVariableValue(String environmentVariable) {
        return factory.environmentVariable(environmentVariable.trim()).forUseAtConfigurationTime();
    }


}
