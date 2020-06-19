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

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class SharedJavaInstallationRegistry {

    private final Set<InstallationSupplier> suppliers = Sets.newConcurrentHashSet();
    private final Supplier<Set<File>> finalizedInstallations = Suppliers.memoize(this::mapToDirectories);

    private boolean finalized;

    @Inject
    public SharedJavaInstallationRegistry(List<InstallationSupplier> suppliers) {
        suppliers.forEach(this::add);
    }

    public void add(InstallationSupplier provider) {
        Preconditions.checkArgument(!finalized, "Installation must not be mutated after being finalized");
        suppliers.add(provider);
    }

    public void finalizeValue() {
        finalized = true;
    }

    public Set<File> listInstallations() {
        finalizeValue();
        return finalizedInstallations.get();
    }

    private Set<File> mapToDirectories() {
        return suppliers.stream().map(InstallationSupplier::get).flatMap(Set::stream).map(InstallationLocation::getLocation).collect(Collectors.toSet());
    }

}
