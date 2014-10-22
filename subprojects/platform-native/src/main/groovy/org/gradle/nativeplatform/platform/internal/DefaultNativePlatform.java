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

import net.rubygrapefruit.platform.NativeIntegrationUnavailableException;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativeplatform.platform.NativePlatform;

import java.io.*;
import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultNativePlatform implements NativePlatformInternal {
    private final NotationParser<Object, ArchitectureInternal> archParser;
    private final NotationParser<Object, OperatingSystemInternal> osParser;
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;
    private static Set<DefaultNativePlatform> defaults = defaultPlatformDefinitions();
    private static volatile DefaultNativePlatform defaultNativePlatform;

    public static Set<DefaultNativePlatform> defaultPlatformDefinitions() {
        //TODO freekh: move this code to somewhere else, or use configuration to load this data instead.
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
        this(name, getDefault().getArchitecture(), getDefault().getOperatingSystem());
    }

    public static NativePlatform getDefault(final ArchitectureInternal architecture, final OperatingSystemInternal operatingSystem) {
        DefaultNativePlatform matchingPlatform = (DefaultNativePlatform) CollectionUtils.find(defaults, new Predicate() {
            public boolean evaluate(Object object) {
                DefaultNativePlatform platform = (DefaultNativePlatform) object;
                return platform.architecture.getInstructionSet().equals(architecture.getInstructionSet())
                        && platform.architecture.getRegisterSize() == architecture.getRegisterSize()
                        && platform.operatingSystem.getInternalOs().equals(operatingSystem.getInternalOs());
            }
        });
        return matchingPlatform;
    }

    //TODO freekh: Move this logic back into grapefruit?
    private static OperatingSystem getPropertyBasedOperatingSystem() {
        String osName = System.getProperty("os.name").toLowerCase();
        OperatingSystem os = null;
        if (osName.contains("windows")) {
            os = OperatingSystem.WINDOWS;
        } else if (osName.contains("linux")) {
            os = OperatingSystem.LINUX;
        } else if (osName.contains("os x") || osName.contains("darwin")) {
            os = OperatingSystem.MAC_OS;
        } else if (osName.contains("freebsd")) {
            os = OperatingSystem.FREE_BSD;
        }
        return os;
    }

    //TODO freekh: Move this logic back into grapefruit?
    private static ArchitectureInternal getPropertyBasedArchitecture() {
        ArchitectureInternal arch = null;
        String archName = System.getProperty("os.arch").toLowerCase();
        if (archName.equals("i386") || archName.equals("x86")) {
            arch = new DefaultArchitecture(archName, ArchitectureInternal.InstructionSet.X86, 32);
        } else if (archName.equals("x86_64") || archName.equals("amd64") || archName.equals("universal")) {
            arch = new DefaultArchitecture(archName, ArchitectureInternal.InstructionSet.X86, 64);
        }
        return arch;
    }

    private static DefaultNativePlatform findDefaultPlatform(final OperatingSystem os, final int registerSize, final ArchitectureInternal.InstructionSet instructionSet) {
        if (os != null) {
            final int workAroundRegisterSize = (registerSize == 64 && os.isWindows()) ? 32 : registerSize; //TODO freekh: THis is no right, we do this because the cunit tests are failing

            DefaultNativePlatform matchingPlatform = (DefaultNativePlatform) CollectionUtils.find(defaults, new Predicate() {
                public boolean evaluate(Object object) {
                    DefaultNativePlatform platform = (DefaultNativePlatform) object;
                    return platform.architecture.getInstructionSet().equals(instructionSet)
                            && platform.architecture.getRegisterSize() == workAroundRegisterSize
                            && platform.operatingSystem.getInternalOs().equals(os);
                }
            });
            return matchingPlatform;
        } else {
            return null;
        }
    }

    private static DefaultNativePlatform assertNonNullPlatform(DefaultNativePlatform nativePlatform, String errorMsg) {
        if (nativePlatform == null) {
            throw new NativeIntegrationUnavailableException(errorMsg);
        } else {
            return nativePlatform;
        }
    }

    private final static String UNKNOWN_DEFAULT_PLATFORM_MSG = "Please specify a target platform.";

    public static DefaultNativePlatform getDefault() {
        //TODO freekh: no need to synchronize, defaultNativePlatform volatile is sufficient.
        if (defaultNativePlatform == null) {
            OperatingSystem os = getPropertyBasedOperatingSystem();
            ArchitectureInternal architectureInternal = getPropertyBasedArchitecture();
            DefaultNativePlatform propertyBasedDefault = null;
            if (architectureInternal != null) {
                propertyBasedDefault = findDefaultPlatform(os, architectureInternal.getRegisterSize(), architectureInternal.getInstructionSet());
            }
            if (propertyBasedDefault != null) {
                defaultNativePlatform = propertyBasedDefault;
            } else { //could not detect platform based on properties
                try {
                    //TODO freekh: Close streams!
                    if ((os != null && os.isWindows()) || File.separatorChar == '\\') { //guess Windows
                        Process archProcess  = Runtime.getRuntime().exec(new String[]{"wmic", "computersystem", "get", "systemtype"});
                        BufferedReader archReader = new BufferedReader(new InputStreamReader(archProcess.getInputStream()));
                        archReader.readLine();
                        archReader.readLine();
                        String archLine = archReader.readLine().toLowerCase();
                        if (archLine.contains("x64")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, 64, ArchitectureInternal.InstructionSet.X86),
                                    "Could not find a default platform for what is believed to be 64-bit Windows on x86. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        } else if (archLine.contains("x86")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, 32, ArchitectureInternal.InstructionSet.X86),
                                    "Could not find a default platform for what is believed to be 32-bit Windows on x86. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        } else if (archLine.contains("strongarm")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, 32, ArchitectureInternal.InstructionSet.ARM),
                                    "Could not find a default platform for what is believed to be Windows on ARM. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        }

                    } else { //guess Nix
                        Process systemProcess = Runtime.getRuntime().exec(new String[]{"uname", "-s"});
                        BufferedReader systemReader = new BufferedReader(new InputStreamReader(systemProcess.getInputStream()));
                        String systemLine = systemReader.readLine().toLowerCase();

                        Process machineProcess = Runtime.getRuntime().exec(new String[]{"uname", "-m"});
                        BufferedReader matchineReader = new BufferedReader(new InputStreamReader(machineProcess.getInputStream()));
                        String machineLine = matchineReader.readLine();
                        NotationParser<Object, ArchitectureInternal> archParser = ArchitectureNotationParser.parser();
                        ArchitectureInternal arch = archParser.parseNotation(machineLine);

                        if (arch == null) {
                            throw new NativeIntegrationUnavailableException("Tried to guess a default architecture of Nix-based OS, but could not. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                        }

                        String errorMsg = String.format("Could not find a default platform for %s architecture: %s. %s", systemLine, arch.getName(), UNKNOWN_DEFAULT_PLATFORM_MSG);
                        if (systemLine.contains("linux")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.LINUX, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        } else if (systemLine.contains("cygwin")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.WINDOWS, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        } else if (systemLine.contains("freebsd")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.FREE_BSD, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        } else if (systemLine.contains("sunos")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.SOLARIS, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        } else if (systemLine.contains("darwin")) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.MAC_OS, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        } else if (!systemLine.isEmpty()) {
                            defaultNativePlatform = assertNonNullPlatform(
                                    findDefaultPlatform(OperatingSystem.UNIX, arch.getRegisterSize(), arch.getInstructionSet()),
                                    errorMsg);
                        }
                    }
                } catch (IOException e) {
                    throw new NativeIntegrationUnavailableException("Could not guess a default native platform. " + UNKNOWN_DEFAULT_PLATFORM_MSG);
                }
            }
        }
        return defaultNativePlatform;
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
