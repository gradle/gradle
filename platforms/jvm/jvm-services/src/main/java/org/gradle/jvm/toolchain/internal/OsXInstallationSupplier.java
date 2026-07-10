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

import org.gradle.internal.os.OperatingSystem;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class OsXInstallationSupplier implements InstallationSupplier {
    private final OsXJavaHomeCommand javaHomeCommand;
    private final OperatingSystem os;
    @Inject
    public OsXInstallationSupplier(OperatingSystem os, OsXJavaHomeCommand javaHomeCommand) {
        this.javaHomeCommand = javaHomeCommand;
        this.os = os;
    }

    @Override
    public String getSourceName() {
        return "MacOS java_home";
    }

    @Override
    public Set<InstallationLocation> get() {
        if (os.isMacOsX()) {
            return javaHomeCommand.findJavaHomes().stream().map(this::asInstallation).collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private InstallationLocation asInstallation(File javaHome) {
        return InstallationLocation.autoDetected(javaHome, getSourceName());
    }

}
