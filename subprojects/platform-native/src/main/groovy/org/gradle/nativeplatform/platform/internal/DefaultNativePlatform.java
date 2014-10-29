/*
 * Copyright 2013 the original author or authors.
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

import net.rubygrapefruit.platform.Native;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultNativePlatform implements NativePlatformInternal {
    private static Set<DefaultNativePlatform> defaults = defaultPlatformDefinitions();
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;

    public static Set<DefaultNativePlatform> defaultPlatformDefinitions() {
        //TODO freekh: move this code to somewhere else, or use configuration to load this data instead.
        //TODO freekh: add more ppc? xbox/playstation is based on Power arch (ppc/cell) I think?
        Set<DefaultNativePlatform> platforms = new LinkedHashSet<DefaultNativePlatform>();

        OperatingSystemInternal windows = new DefaultOperatingSystem("windows");
        OperatingSystemInternal freebsd = new DefaultOperatingSystem("freebsd");
        OperatingSystemInternal linux = new DefaultOperatingSystem("linux");
        OperatingSystemInternal osx = new DefaultOperatingSystem("osx");
        OperatingSystemInternal unix = new DefaultOperatingSystem("unix");
        OperatingSystemInternal solaris = new DefaultOperatingSystem("solaris");

        ArchitectureInternal x86 = Architectures.forInput("x86");
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
        String name = String.format("%s_%s", os.getName(), arch.getName());
        return new DefaultNativePlatform(name, os, arch);
    }

    public DefaultNativePlatform(String name) {
        this(name, getDefault().getOperatingSystem(), getDefault().getArchitecture());
    }

    protected DefaultNativePlatform(String name, OperatingSystemInternal operatingSystem, ArchitectureInternal architecture) {
        this.name = name;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
    }

    public static DefaultNativePlatform getDefault() {
        OperatingSystemInternal os = new DefaultOperatingSystem(System.getProperty("os.name"), OperatingSystem.current());
        ArchitectureInternal architecture = Architectures.forInput(Native.get(SystemInfo.class).getArchitecture().toString());

        // TODO:DAZ This is a very limited implementation of defaults, just to get the build passing
        if (os.isWindows()) {
            // Always use x86 as default on windows
            return findWithName("windows_x86");
        }
        if (os.isLinux()) {
            return findWithName("linux_" + architecture.getName());
        }
        if (os.isMacOsX()) {
            return findWithName("osx_" + architecture.getName());
        }
        return findWithName("unix_x86");
    }

    private static DefaultNativePlatform findWithName(final String name) {
        return org.gradle.util.CollectionUtils.findFirst(defaults, new Spec<DefaultNativePlatform>() {
            public boolean isSatisfiedBy(DefaultNativePlatform element) {
                return element.getName().equals(name);
            }
        });
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    public String getDisplayName() {
        return String.format("platform '%s'", name);
    }

    public ArchitectureInternal getArchitecture() {
        return architecture;
    }

    public void architecture(String name) {
        architecture = Architectures.forInput(name);
    }

    public OperatingSystemInternal getOperatingSystem() {
        return operatingSystem;
    }

    public void operatingSystem(String name) {
        operatingSystem = new DefaultOperatingSystem(name);
    }

    public String getCompatibilityString() {
        return String.format("%s:%s", getArchitecture().getName(), getOperatingSystem().getName());
    }
}
