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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.inject.Inject;
import java.nio.file.InvalidPathException;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class LocationListInstallationSupplier implements InstallationSupplier {

    public static final String JAVA_INSTALLATIONS_PATHS_PROPERTY = "org.gradle.java.installations.paths";

    private static final Logger LOGGER = Logging.getLogger(LocationListInstallationSupplier.class);

    private final ToolchainConfiguration buildOptions;
    private final FileResolver fileResolver;

    @Inject
    public LocationListInstallationSupplier(ToolchainConfiguration buildOptions, FileResolver fileResolver) {
        this.buildOptions = buildOptions;
        this.fileResolver = fileResolver;
    }

    @Override
    public String getSourceName() {
        return "Gradle property '" + JAVA_INSTALLATIONS_PATHS_PROPERTY + "'";
    }

    @Override
    public Set<InstallationLocation> get() {
        final Collection<String> property = buildOptions.getInstallationsFromPaths();
        return property.stream()
            .filter(path -> !path.trim().isEmpty())
            .map(this::resolveInstallation)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    }

    private Optional<InstallationLocation> resolveInstallation(String candidate) {
        try {
            return Optional.of(InstallationLocation.userDefined(fileResolver.resolve(candidate), getSourceName()));
        } catch (UnsupportedOperationException | InvalidPathException e) {
            LOGGER.warn("Invalid path '{}' provided for {} could not be resolved: {}", candidate, getSourceName(), e.getMessage());
            return Optional.empty();
        }
    }

}
