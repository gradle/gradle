/*
 * Copyright 2024 the original author or authors.
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

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ScoopInstallationSupplier implements InstallationSupplier {

    private final ToolchainConfiguration toolchainConfiguration;

    @Inject
    public ScoopInstallationSupplier(ToolchainConfiguration toolchainConfiguration) {
        this.toolchainConfiguration = toolchainConfiguration;
    }

    @Override
    public String getSourceName() {
        return "Scoop";
    }

    @Override
    public Set<InstallationLocation> get() {
        return findJavaCandidates(toolchainConfiguration.getScoopDirectory());
    }

    private Set<InstallationLocation> findJavaCandidates(File scoopDir) {
        final File appsDir = new File(scoopDir, "apps");
        // Contains all apps, including non JDK ones... we should filter!
        File[] scoopApps = appsDir.listFiles();
        if(scoopApps == null) { // No scoop
            return Collections.emptySet();
        }
        Set<InstallationLocation> potentialInstallationLocations = new HashSet<>();
        for (File scoopApp : scoopApps) {
            File potentialJavaC = new File(scoopApp, "current/bin/javac.exe"); //Scoop is windows only
            if(potentialJavaC.exists()) {
                potentialInstallationLocations.add(InstallationLocation.autoDetected(new File(scoopApp, "current"), getSourceName()));
            }
        }
        return potentialInstallationLocations;
    }
}
