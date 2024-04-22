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
import org.gradle.internal.os.OperatingSystem;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class LinuxInstallationSupplier implements InstallationSupplier {
    @VisibleForTesting
    final File[] roots;
    private final OperatingSystem os;

    @Inject
    public LinuxInstallationSupplier() {
        this(OperatingSystem.current(), new File("/usr/lib/jvm"), new File("/usr/lib64/jvm"), new File("/usr/java"), new File("/usr/local/java"), new File("/opt/java"));
    }

    @VisibleForTesting
    LinuxInstallationSupplier(OperatingSystem os, File... roots) {
        this.roots = roots;
        this.os = os;
    }

    @Override
    public String getSourceName() {
        return "Common Linux Locations";
    }

    @Override
    public Set<InstallationLocation> get() {
        if (os.isLinux()) {
            return Arrays.stream(roots)
                .map(root -> FileBasedInstallationFactory.fromDirectory(root, getSourceName(), InstallationLocation::autoDetected))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }
}
