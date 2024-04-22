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

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Set;

public class JabbaInstallationSupplier implements InstallationSupplier {
    private final ToolchainConfiguration toolchainConfiguration;

    @Inject
    public JabbaInstallationSupplier(ToolchainConfiguration toolchainConfiguration) {
        this.toolchainConfiguration = toolchainConfiguration;
    }

    @Override
    public String getSourceName() {
        return "Jabba";
    }

    @Override
    public Set<InstallationLocation> get() {
        return findJavaCandidates(toolchainConfiguration.getJabbaHomeDirectory());
    }

    private Set<InstallationLocation> findJavaCandidates(@Nullable File candidatesDir) {
        if (candidatesDir != null) {
            final File root = new File(candidatesDir, "jdk");
            return FileBasedInstallationFactory.fromDirectory(root, getSourceName(), InstallationLocation::autoDetected);
        } else {
            return Collections.emptySet();
        }
    }
}
