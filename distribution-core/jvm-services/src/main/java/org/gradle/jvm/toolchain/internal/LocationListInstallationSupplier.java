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
import java.util.Set;
import java.util.stream.Collectors;

public class LocationListInstallationSupplier implements InstallationSupplier {

    private static final String PROPERTY_NAME = "org.gradle.java.installations.paths";

    private final ProviderFactory factory;

    @Inject
    public LocationListInstallationSupplier(ProviderFactory factory) {
        this.factory = factory;
    }

    @Override
    public Set<InstallationLocation> get() {
        final Provider<String> property = factory.gradleProperty(PROPERTY_NAME).forUseAtConfigurationTime();
        return property.map(paths -> asInstallations(paths)).orElse(Collections.emptySet()).get();
    }

    private Set<InstallationLocation> asInstallations(String listOfDirectories) {
        return Arrays.stream(listOfDirectories.split(","))
            .filter(path -> !path.trim().isEmpty())
            .map(path -> new InstallationLocation(new File(path), "system property '" + PROPERTY_NAME + "'"))
            .collect(Collectors.toSet());
    }

}
