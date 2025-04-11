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

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class EnvironmentVariableListInstallationSupplier implements InstallationSupplier {

    public static final String JAVA_INSTALLATIONS_FROM_ENV_PROPERTY = "org.gradle.java.installations.fromEnv";

    private final ToolchainConfiguration buildOptions;
    private final FileResolver fileResolver;

    @Inject
    public EnvironmentVariableListInstallationSupplier(ToolchainConfiguration buildOptions, FileResolver fileResolver) {
        this.buildOptions = buildOptions;
        this.fileResolver = fileResolver;
    }

    @Override
    public String getSourceName() {
        return "environment variables from gradle property '" + JAVA_INSTALLATIONS_FROM_ENV_PROPERTY + "'";
    }

    @Override
    public Set<InstallationLocation> get() {
        final Collection<String> possibleInstallations = buildOptions.getJavaInstallationsFromEnvironment();
        return possibleInstallations.stream().map(this::resolveEnvironmentVariable)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<InstallationLocation> resolveEnvironmentVariable(String environmentVariable) {
        final String value = environmentVariableValue(environmentVariable);
        if (value != null) {
            final String path = value.trim();
            if (!path.isEmpty()) {
                return Optional.of(InstallationLocation.userDefined(fileResolver.resolve(path), "environment variable '" + environmentVariable + "'"));
            }
        }
        return Optional.empty();
    }

    @Nullable
    private String environmentVariableValue(String environmentVariable) {
        return buildOptions.getEnvironmentVariableValue(environmentVariable.trim());
    }


}
