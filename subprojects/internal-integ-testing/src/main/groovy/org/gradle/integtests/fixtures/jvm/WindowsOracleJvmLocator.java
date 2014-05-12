/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.integtests.fixtures.jvm;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.JavaVersion;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Uses windows registry to find installed Sun/Oracle JVMs
 */
class WindowsOracleJvmLocator {
    private final WindowsRegistry windowsRegistry;
    private final SystemInfo systemInfo;

    WindowsOracleJvmLocator(WindowsRegistry windowsRegistry, SystemInfo systemInfo) {
        this.windowsRegistry = windowsRegistry;
        this.systemInfo = systemInfo;
    }

    public Collection<JvmInstallation> findJvms() {
        JvmInstallation.Arch defaultArch = systemInfo.getArchitecture() == SystemInfo.Architecture.i386 ? JvmInstallation.Arch.i386 : JvmInstallation.Arch.x86_64;
        List<JvmInstallation> jvms = new ArrayList<JvmInstallation>();
        findJvms(windowsRegistry, "SOFTWARE\\JavaSoft\\Java Development Kit", jvms, true, defaultArch);
        findJvms(windowsRegistry, "SOFTWARE\\JavaSoft\\Java Runtime Environment", jvms, false, defaultArch);
        findJvms(windowsRegistry, "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit", jvms, true, JvmInstallation.Arch.i386);
        findJvms(windowsRegistry, "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment", jvms, false, JvmInstallation.Arch.i386);
        return jvms;
    }

    private void findJvms(WindowsRegistry windowsRegistry, String sdkSubkey, Collection<JvmInstallation> jvms, boolean jdk, JvmInstallation.Arch arch) {
        List<String> versions;
        try {
            versions = windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey);
        } catch (MissingRegistryEntryException e) {
            // Ignore
            return;
        }

        for (String version : versions) {
            if (version.matches("\\d+\\.\\d+")) {
                continue;
            }
            String javaHome = windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey + '\\' + version, "JavaHome");
            jvms.add(new JvmInstallation(JavaVersion.toVersion(version), version, new File(javaHome), jdk, arch));
        }
    }
}
