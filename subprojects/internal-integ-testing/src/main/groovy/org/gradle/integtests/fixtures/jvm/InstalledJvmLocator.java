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

import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.nativeintegration.filesystem.FileCanonicalizer;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.*;

public class InstalledJvmLocator {

    private final OperatingSystem operatingSystem;
    private final Jvm currentJvm;
    private final WindowsRegistry windowsRegistry;
    private final SystemInfo systemInfo;
    private final FileCanonicalizer fileCanonicalizer;

    public InstalledJvmLocator(OperatingSystem currentOperatingSystem, Jvm currentJvm, WindowsRegistry windowsRegistry, SystemInfo systemInfo, FileCanonicalizer fileCanonicalizer) {
        this.operatingSystem = currentOperatingSystem;
        this.currentJvm = currentJvm;
        this.windowsRegistry = windowsRegistry;
        this.systemInfo = systemInfo;
        this.fileCanonicalizer = fileCanonicalizer;
    }

    /**
     * Discovers JVMs installed on the local machine. Returns the details of each JVM that can be determined efficiently, without running the JVM.
     *
     * @return The JVMs, ordered from highest to lowest Java version. Will include the current JVM.
     */
    public List<JvmInstallation> findJvms() {
        Map<File, JvmInstallation> installs = new HashMap<File, JvmInstallation>();
        Collection<JvmInstallation> jvms;
        if (operatingSystem.isMacOsX()) {
            jvms = new OsXInstalledJvmLocator(TestFiles.execHandleFactory()).findJvms();
        } else if (operatingSystem.isWindows()) {
            jvms = new WindowsOracleJvmLocator(windowsRegistry, systemInfo).findJvms();
        } else if (operatingSystem.isLinux()) {
            jvms = new UbuntuJvmLocator(fileCanonicalizer).findJvms();
        } else {
            jvms = Collections.emptySet();
        }
        for (JvmInstallation jvm : jvms) {
            if (!installs.containsKey(jvm.getJavaHome())) {
                installs.put(jvm.getJavaHome(), jvm);
            }
        }
        if (!installs.containsKey(currentJvm.getJavaHome())) {
            // TODO - this isn't quite right
            boolean isJdk = currentJvm.getJre() == null || !currentJvm.getJre().getHomeDir().equals(currentJvm.getJavaHome());
            installs.put(currentJvm.getJavaHome(), new JvmInstallation(currentJvm.getJavaVersion(), System.getProperty("java.version"), currentJvm.getJavaHome(), isJdk, toArch(System.getProperty("os.arch"))));
        }

        List<JvmInstallation> result = new ArrayList<JvmInstallation>(installs.values());
        Collections.sort(result, new Comparator<JvmInstallation>() {
            @Override
            public int compare(JvmInstallation o1, JvmInstallation o2) {
                return o2.getVersion().compareTo(o1.getVersion());
            }
        });
        return result;
    }

    private JvmInstallation.Arch toArch(String arch) {
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return JvmInstallation.Arch.x86_64;
        }
        if (arch.equals("i386")) {
            return JvmInstallation.Arch.i386;
        }
        return JvmInstallation.Arch.Unknown;
    }
}
