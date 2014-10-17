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
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultNativePlatform implements NativePlatformInternal {
    private final NotationParser<Object, ArchitectureInternal> archParser;
    private final NotationParser<Object, OperatingSystemInternal> osParser;
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;
    private static Set<DefaultNativePlatform> defaults = defaultPlatformDefinitions();

    public static Set<DefaultNativePlatform> defaultPlatformDefinitions() {
        //TODO freekh: add itanium? Who on earth uses it these days? It was discontinued in 2012 so...
        //TODO freekh: add more ppc? xbox/playstation is based on Power arch (ppc/cell) I think?
        Set<DefaultNativePlatform> platforms = new LinkedHashSet<DefaultNativePlatform>();

        OperatingSystemInternal windows = new DefaultOperatingSystem("windows", OperatingSystem.WINDOWS);
        OperatingSystemInternal freebsd = new DefaultOperatingSystem("freebsd", OperatingSystem.FREE_BSD);
        OperatingSystemInternal linux = new DefaultOperatingSystem("linux", OperatingSystem.LINUX);
        OperatingSystemInternal osx = new DefaultOperatingSystem("osx", OperatingSystem.MAC_OS);
        OperatingSystemInternal unix = new DefaultOperatingSystem("unix", OperatingSystem.UNIX);
        OperatingSystemInternal solaris = new DefaultOperatingSystem("solaris", OperatingSystem.SOLARIS);

        ArchitectureInternal x64 = new DefaultArchitecture("x64", ArchitectureInternal.InstructionSet.X86, 64);
        ArchitectureInternal x86 = new DefaultArchitecture("x86", ArchitectureInternal.InstructionSet.X86, 32);
        ArchitectureInternal armv7 = new DefaultArchitecture("armv7", ArchitectureInternal.InstructionSet.ARM, 32);
        ArchitectureInternal armv8 = new DefaultArchitecture("armv8", ArchitectureInternal.InstructionSet.ARM, 64);
        ArchitectureInternal sparc = new DefaultArchitecture("sparc", ArchitectureInternal.InstructionSet.SPARC, 32);
        ArchitectureInternal ultrasparc = new DefaultArchitecture("ultrasparc", ArchitectureInternal.InstructionSet.SPARC, 64);
        ArchitectureInternal ppc = new DefaultArchitecture("ppc", ArchitectureInternal.InstructionSet.PPC, 32);
        ArchitectureInternal ppc64 = new DefaultArchitecture("ppc64", ArchitectureInternal.InstructionSet.PPC, 64);


        platforms.add(new DefaultNativePlatform("windows_x64", x64, windows));
        platforms.add(new DefaultNativePlatform("windows_x86", x86, windows));
        platforms.add(new DefaultNativePlatform("windows_rt_32", armv7, windows));

        platforms.add(new DefaultNativePlatform("freebsd_x64", x64, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_x86", x86, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_armv7", armv7, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_armv8", armv8, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_ppc", ppc, freebsd));
        platforms.add(new DefaultNativePlatform("freebsd_ppc64", ppc64, freebsd));

        platforms.add(new DefaultNativePlatform("unix_x64", x64, unix));
        platforms.add(new DefaultNativePlatform("unix_x86", x86, unix));
        platforms.add(new DefaultNativePlatform("unix_armv7", armv7, unix));
        platforms.add(new DefaultNativePlatform("unix_armv8", armv8, unix));
        platforms.add(new DefaultNativePlatform("unix_ppc", ppc, unix));
        platforms.add(new DefaultNativePlatform("unix_ppc64", ppc64, unix));

        platforms.add(new DefaultNativePlatform("linux_x64", x64, linux));
        platforms.add(new DefaultNativePlatform("linux_x86", x86, linux));
        platforms.add(new DefaultNativePlatform("linux_armv7", armv7, linux));
        platforms.add(new DefaultNativePlatform("linux_armv8", armv8, linux));

        platforms.add(new DefaultNativePlatform("osx_x86", x86, osx));
        platforms.add(new DefaultNativePlatform("osx_x64", x64, osx));

        platforms.add(new DefaultNativePlatform("solaris_x64", x64, solaris));
        platforms.add(new DefaultNativePlatform("solaris_x86", x86, solaris));
        platforms.add(new DefaultNativePlatform("solaris_sparc", sparc, solaris));
        platforms.add(new DefaultNativePlatform("solaris_ultrasparc", ultrasparc, solaris));

        return platforms;
    }

    public DefaultNativePlatform(String name, ArchitectureInternal architecture, OperatingSystemInternal operatingSystem, NotationParser<Object, ArchitectureInternal> archParser, NotationParser<Object, OperatingSystemInternal> osParser) {
        this.name = name;
        this.archParser = archParser;
        this.osParser = osParser;
        this.architecture = architecture;
        this.operatingSystem = operatingSystem;
    }

    public DefaultNativePlatform(String name, ArchitectureInternal architecture, OperatingSystemInternal operatingSystem) {
       this(name, architecture, operatingSystem, ArchitectureNotationParser.parser(), OperatingSystemNotationParser.parser());
    }

    public DefaultNativePlatform(String name) {
        this(name, getCurrentArchitecture(), getCurrentOs());
    }

    public static String getDefaultName(final ArchitectureInternal architecture, final OperatingSystemInternal operatingSystem) {
        DefaultNativePlatform matchingPlatform = (DefaultNativePlatform) CollectionUtils.find(defaults, new Predicate() {
            public boolean evaluate(Object object) {
                DefaultNativePlatform platform = (DefaultNativePlatform) object;

                return platform.architecture.getInstructionSet().equals(architecture.getInstructionSet()) &&
                        platform.architecture.getRegisterSize() == architecture.getRegisterSize() &&
                        platform.operatingSystem.getInternalOs().equals(operatingSystem.getInternalOs());
            }
        });
        return matchingPlatform.getName();
    }

    public static DefaultNativePlatform getDefault() {
        ArchitectureInternal architecture = getCurrentArchitecture();
        OperatingSystem currentOs = OperatingSystem.current();
        OperatingSystemInternal operatingSystem = new DefaultOperatingSystem(currentOs.getName(), currentOs);
        return new DefaultNativePlatform(getDefaultName(architecture, operatingSystem), architecture, operatingSystem);
    }

    private static ArchitectureInternal getCurrentArchitecture() {
        NotationParser<Object, ArchitectureInternal> archParser = ArchitectureNotationParser.parser(); //TODO freekh: this looks weird, but it seemed like the best way to create an ArchitectureInternal
        String archName = Native.get(SystemInfo.class).getArchitecture().toString();
        return archParser.parseNotation(archName);
    }

    private static OperatingSystemInternal getCurrentOs() {
        OperatingSystem currentOs = OperatingSystem.current();
        return new DefaultOperatingSystem(currentOs.getName(), currentOs); //TODO freekh: this looks weird, but it seemed like the best way to create an OperatingSystemInternal
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

    public void architecture(Object notation) {
        architecture = archParser.parseNotation(notation);
    }

    public OperatingSystemInternal getOperatingSystem() {
        return operatingSystem;
    }

    public void operatingSystem(Object notation) {
        operatingSystem = osParser.parseNotation(notation);
    }

    public String getCompatibilityString() {
        return String.format("%s:%s", getArchitecture().getName(), getOperatingSystem().getName());
    }
}
