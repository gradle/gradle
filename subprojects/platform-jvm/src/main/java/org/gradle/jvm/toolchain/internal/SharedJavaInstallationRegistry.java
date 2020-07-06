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
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;


public class SharedJavaInstallationRegistry {

    private final Set<InstallationSupplier> suppliers = Sets.newConcurrentHashSet();
    private final Supplier<Set<File>> finalizedInstallations = Suppliers.memoize(this::mapToDirectories);
    private final AtomicBoolean finalized = new AtomicBoolean();
    private final Logger logger;

    @Inject
    public SharedJavaInstallationRegistry(List<InstallationSupplier> suppliers) {
        this(suppliers, Logging.getLogger(SharedJavaInstallationRegistry.class));

    }

    private SharedJavaInstallationRegistry(List<InstallationSupplier> suppliers, Logger logger) {
        this.suppliers.addAll(suppliers);
        this.logger = logger;
    }

    @VisibleForTesting
    static SharedJavaInstallationRegistry withLogger(Logger logger) {
        return new SharedJavaInstallationRegistry(Collections.emptyList(), logger);
    }

    void add(InstallationSupplier provider) {
        Preconditions.checkArgument(!finalized.get(), "Installation must not be mutated after being finalized");
        suppliers.add(provider);
    }

    public Set<File> listInstallations() {
        finalizeValue();
        return finalizedInstallations.get();
    }

    private void finalizeValue() {
        finalized.compareAndSet(false, true);
    }

    private Set<File> mapToDirectories() {
        return suppliers.stream()
            .map(InstallationSupplier::get)
            .flatMap(Set::stream)
            .filter(this::installationExists)
            .map(InstallationLocation::getLocation)
            .collect(Collectors.toSet());
    }

    boolean installationExists(InstallationLocation installationLocation) {
        File file = installationLocation.getLocation();
        if (!file.exists()) {
            logger.warn("Directory {} used for java installations does not exist", installationLocation.getDisplayName());
            return false;
        }
        if (!file.isDirectory()) {
            logger.warn("Path for java installation {} points to a file, not a directory", installationLocation.getDisplayName());
            return false;
        }
        return true;
    }

}
