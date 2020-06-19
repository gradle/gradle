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

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class LocationListInstallationSupplier implements InstallationSupplier {

    private final Logger logger;
    private final ProviderFactory factory;

    @Inject
    public LocationListInstallationSupplier(ProviderFactory factory) {
        this(factory, Logging.getLogger(LocationListInstallationSupplier.class));
    }

    private LocationListInstallationSupplier(ProviderFactory factory, Logger logger) {
        this.factory = factory;
        this.logger = logger;
    }

    public static LocationListInstallationSupplier withLogger(ProviderFactory factory, Logger logger) {
        return new LocationListInstallationSupplier(factory, logger);
    }

    @Override
    public Set<InstallationLocation> get() {
        final String propertyName = "org.gradle.java.installations.paths";
        final Provider<String> property = factory.gradleProperty(propertyName).forUseAtConfigurationTime();
        if (property.isPresent()) {
            final String listOfDirectories = property.get();
            return Arrays.stream(listOfDirectories.split(",")).map(File::new).filter(this::pathMayBeValid).map(file -> new InstallationLocation(file, "system property '" + propertyName + "'")).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    boolean pathMayBeValid(File file) {
        if (!file.exists()) {
            logger.warn("Directory '" + file.getAbsolutePath() +
                "' used for java installations does not exist");
            return false;
        }
        if (!file.isDirectory()) {
            logger.warn("Path for java installation '" + file.getAbsolutePath() +
                "' points to a file, not a directory");
            return false;
        }
        return true;
    }

}
