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

package org.gradle.nativeplatform.platform.internal;

import java.util.LinkedHashSet;
import java.util.Set;

public class NativePlatforms {

    private static final String OS_WINDOWS = "windows";
    private static final String OS_LINUX = "linux";
    private static final String OS_OSX = "osx";
    private static final String OS_UNIX = "unix";
    private static final String ARCH_X86 = "x86";

    public Set<DefaultNativePlatform> defaultPlatformDefinitions() {
        Set<DefaultNativePlatform> platforms = new LinkedHashSet<DefaultNativePlatform>();

        OperatingSystemInternal windows = new DefaultOperatingSystem(OS_WINDOWS);
        OperatingSystemInternal linux = new DefaultOperatingSystem(OS_LINUX);
        OperatingSystemInternal osx = new DefaultOperatingSystem(OS_OSX);
        OperatingSystemInternal unix = new DefaultOperatingSystem(OS_UNIX);
        OperatingSystemInternal freebsd = new DefaultOperatingSystem("freebsd");
        OperatingSystemInternal solaris = new DefaultOperatingSystem("solaris");

        ArchitectureInternal x86 = Architectures.forInput(ARCH_X86);
        ArchitectureInternal x64 = Architectures.forInput("x86_64");
        ArchitectureInternal ia64 = Architectures.forInput("ia64");
        ArchitectureInternal armv7 = Architectures.forInput("armv7");
        ArchitectureInternal armv8 = Architectures.forInput("armv8");
        ArchitectureInternal sparc = Architectures.forInput("sparc");
        ArchitectureInternal ultrasparc = Architectures.forInput("ultrasparc");
        ArchitectureInternal ppc = Architectures.forInput("ppc");
        ArchitectureInternal ppc64 = Architectures.forInput("ppc64");

        platforms.add(createPlatform(windows, x86));
        platforms.add(createPlatform(windows, x64));
        platforms.add(createPlatform(windows, armv7));
        platforms.add(createPlatform(windows, ia64));

        platforms.add(createPlatform(freebsd, x86));
        platforms.add(createPlatform(freebsd, x64));
        platforms.add(createPlatform(freebsd, armv7));
        platforms.add(createPlatform(freebsd, armv8));
        platforms.add(createPlatform(freebsd, ppc));
        platforms.add(createPlatform(freebsd, ppc64));

        platforms.add(createPlatform(unix, x86));
        platforms.add(createPlatform(unix, x64));
        platforms.add(createPlatform(unix, armv7));
        platforms.add(createPlatform(unix, armv8));
        platforms.add(createPlatform(unix, ppc));
        platforms.add(createPlatform(unix, ppc64));

        platforms.add(createPlatform(linux, x64));
        platforms.add(createPlatform(linux, x86));
        platforms.add(createPlatform(linux, armv7));
        platforms.add(createPlatform(linux, armv8));

        platforms.add(createPlatform(osx, x86));
        platforms.add(createPlatform(osx, x64));

        platforms.add(createPlatform(solaris, x64));
        platforms.add(createPlatform(solaris, x86));
        platforms.add(createPlatform(solaris, sparc));
        platforms.add(createPlatform(solaris, ultrasparc));

        return platforms;
    }

    private static DefaultNativePlatform createPlatform(OperatingSystemInternal os, ArchitectureInternal arch) {
        return new DefaultNativePlatform(platformName(os.getName(), arch.getName()), os, arch);
    }

    private static String platformName(String os, String arch) {
        return os + "_" + arch;
    }

    public String getDefaultPlatformName() {
        NativePlatformInternal defaultPlatform = new DefaultNativePlatform("default");
        OperatingSystemInternal os = defaultPlatform.getOperatingSystem();
        ArchitectureInternal architecture = defaultPlatform.getArchitecture();

        if (os.isWindows()) {
            // Always use x86 as default on windows
            return platformName(OS_WINDOWS, ARCH_X86);
        }
        if (os.isLinux()) {
            return platformName(OS_LINUX, architecture.getName());
        }
        if (os.isMacOsX()) {
            return platformName(OS_OSX, architecture.getName());
        }
        return platformName(OS_UNIX, ARCH_X86);
    }
}
