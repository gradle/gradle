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
import org.gradle.api.file.Directory;

import java.util.Set;
import java.util.stream.Collectors;

import static org.gradle.jvm.toolchain.internal.InstallationProviders.InstallationProvider;

public class SharedJavaInstallationRegistry {

    private final Set<InstallationProvider> providers = Sets.newConcurrentHashSet();
    private final Supplier<Set<Directory>> finalizedInstallations = Suppliers.memoize(this::mapToDirectories);

    private boolean finalized;

    public void add(InstallationProvider provider) {
        Preconditions.checkArgument(!finalized, "Installation must not be mutated after being finalized");
        providers.add(provider);
    }

    public void finalizeValue() {
        finalized = true;
    }

    public Set<Directory> listInstallations() {
        finalizeValue();
        return finalizedInstallations.get();
    }

    private Set<Directory> mapToDirectories() {
        return providers.stream().map(InstallationProvider::resolvePath).collect(Collectors.toSet());
    }

}
