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
import java.util.Set;
import java.util.stream.Collectors;

public class LocationListInstallationSupplier implements InstallationSupplier {

    private static final String JAVA_INSTALLATIONS_PATHS_PROPERTY = "org.gradle.java.installations.paths";

    private final ProviderFactory factory;
    private final FileResolver fileResolver;

    @Inject
    public LocationListInstallationSupplier(ProviderFactory factory, FileResolver fileResolver) {
        this.factory = factory;
        this.fileResolver = fileResolver;
    }

    @Override
    public Set<InstallationLocation> get() {
        final Provider<String> property = factory.gradleProperty(JAVA_INSTALLATIONS_PATHS_PROPERTY);
        return property.map(paths -> asInstallations(paths)).orElse(Collections.emptySet()).get();
    }

    private Set<InstallationLocation> asInstallations(String listOfDirectories) {
        return Arrays.stream(listOfDirectories.split(","))
            .filter(path -> !path.trim().isEmpty())
            .map(path -> new InstallationLocation(fileResolver.resolve(path), "system property '" + JAVA_INSTALLATIONS_PATHS_PROPERTY + "'"))
            .collect(Collectors.toSet());
    }

}
