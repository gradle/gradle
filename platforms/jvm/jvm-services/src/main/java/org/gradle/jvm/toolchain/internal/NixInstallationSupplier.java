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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class NixInstallationSupplier implements InstallationSupplier {
    private static final Logger LOGGER = Logging.getLogger(NixInstallationSupplier.class);

    private static final String STORE_PATH_PROPERTY_NAME = "org.gradle.java.installations.nix.store";
    private static final String STORE_PATH_DEFAULT = "/nix/store";

    // Paths will be of the form "{hash}-{suffix}", for example:
    //
    //     /nix/store/6375rn8kiq9pn4pgdkdiqgvg8b0gdycy-openjdk-19.0.2+7
    //
    // We know the the length of Nix store hashes (32),
    // and the length of a hyphen (1), and only look at the suffix.
    private static final int PREFIX_LENGTH = 33;

    private static final Predicate<String> PATTERN = Pattern.compile("jdk", Pattern.CASE_INSENSITIVE).asPredicate();

    private static final FileFilter FILTER = (final File file) -> {
        final String name = file.getName();
        if (name.length() < PREFIX_LENGTH) {
            return false;
        }
        return PATTERN.test(name.substring(PREFIX_LENGTH));
    };

    @VisibleForTesting
    final File store;

    @Inject
    public NixInstallationSupplier(ProviderFactory providerFactory) {
        final OperatingSystem os = OperatingSystem.current();

        // As of 2024-05, Nix is only officially supported on Linux and MacOS.
        if (!os.isLinux() && !os.isMacOsX()) {
            LOGGER.trace("Operating system not supported.");
            store = null;
            return;
        }

        File store = null;
        
        final Provider<String> storeProvider = providerFactory.gradleProperty(STORE_PATH_PROPERTY_NAME);
        if (storeProvider.isPresent()) {
            try {
                store = new File(storeProvider.get());
                if (!store.exists()) {
                    store = null;
                    LOGGER.warn("Initialization for store path '{}' failed. Path does not exist. Falling back to default '{}'.", store, STORE_PATH_DEFAULT);
                }
            } catch (Exception e) {
                store = null;
                final String message = "Initialization for store path '{}' failed. Falling back to default '{}'.";
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(message, store, STORE_PATH_DEFAULT, e);
                } else {
                    LOGGER.warn(message, store, STORE_PATH_DEFAULT);
                }
            }
            if (store != null) {
                this.store = store;
                return;
            }
        }

        try {
            store = new File(STORE_PATH_DEFAULT);
            if (!store.exists()) {
                store = null;
                LOGGER.debug("Initialization for default store path '{}' failed. Path does not exist.", STORE_PATH_DEFAULT);
            }
        } catch (Exception e) {
            LOGGER.debug("Initialization for default store path '{}' failed.", STORE_PATH_DEFAULT, e);
        }

        this.store = store;
    }

    @Override
    public Set<InstallationLocation> get() {
        if (store == null) {
            LOGGER.trace("Nothing to do since `store` is `null`.");
            return Collections.emptySet();
        }

        final File[] list = store.listFiles(FILTER);
        if (list == null) {
            LOGGER.debug("Listing files within store path '{}' failed.");
            return Collections.emptySet();
        }

        final HashSet<InstallationLocation> locations = new HashSet<InstallationLocation>(list.length);
        for (final File file : list) {
            locations.add(InstallationLocation.autoDetected(file, getSourceName()));
        }
        return locations;
    }

    @Override
    public String getSourceName() {
        return "Nix";
    }

    @VisibleForTesting
    NixInstallationSupplier(File store) {
        this.store = store;
    }
}
