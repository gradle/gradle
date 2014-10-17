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
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;

public class DefaultNativePlatform implements NativePlatformInternal {
    private final NotationParser<Object, ArchitectureInternal> archParser;
    private final NotationParser<Object, OperatingSystemInternal> osParser;
    private final String name;
    private ArchitectureInternal architecture;
    private OperatingSystemInternal operatingSystem;

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

    public static DefaultNativePlatform create(String name) {
        //TODO freekh: There is something weird about this code, but then again...
        NotationParser<Object, ArchitectureInternal> archParser = ArchitectureNotationParser.parser();
        ArchitectureInternal architecture;
        //TODO freekh: parsing like this is not what we want
        String[] parts = name.toLowerCase().split("_");
        //TODO freekh: At least this part!
        if (name.toLowerCase().contains("windows")) { //special rules for windows
            if (name.toLowerCase().contains("rt")) {
                architecture = new DefaultArchitecture("armv7", ArchitectureInternal.InstructionSet.ARM, 32);
            } else {
                architecture = new DefaultArchitecture("x64", ArchitectureInternal.InstructionSet.X86, 64);
            }
        } else {
            architecture = archParser.parseNotation(parts[parts.length - 1]);
        }
        String osPart = parts[0];
        NotationParser<Object, OperatingSystemInternal> osParser = OperatingSystemNotationParser.parser();
        return new DefaultNativePlatform(name, architecture, osParser.parseNotation(osPart));
    }

    public static String getDefaultName(ArchitectureInternal architecture, OperatingSystemInternal currentOs) {
        //TODO freekh: create a mapping and a reverse mapping

        //TODO freekh: add itanium? Who on earth uses it these days? It was discontinued in 2012 so...
        //TODO freekh: add more ppc? xbox/playstation is based on Power arch (ppc/cell) I think?
        if (currentOs.isWindows()) { //WINDOWS
            return "windows_x64";
//        } else if (currentOs.isWindows() && architecture.isAmd64()) { //TODO freekh: for now always use i386 for windows
//            return "windows_x64";
        } else if (currentOs.isWindows() && architecture.isArm()) {
            return "windows_rt_32";
        } else if (currentOs.isFreeBSD() && architecture.isAmd64()) { //FREE BSD
            return "freebsd_x64";
        } else if (currentOs.isFreeBSD() && architecture.isI386()) {
            return "freebsd_x86";
        } else if (currentOs.isFreeBSD() && architecture.isArm()) {
            return "freebsd_armv7";
        } else if (currentOs.isFreeBSD() && architecture.isArmv8()) {
            return "freebsd_armv8";
        } else if (currentOs.isFreeBSD() && architecture.isPpc()) {
            return "freebsd_ppc";
        } else if (currentOs.isFreeBSD() && architecture.isPpc64()) {
            return "freebsd_ppc64";
        } else if (currentOs.isLinux() && architecture.isAmd64()) { //LINUX
            return "linux_x64";
        } else if (currentOs.isLinux() && architecture.isI386()) {
            return "linux_x86";
        } else if (currentOs.isLinux() && architecture.isArm()) {
            return "linux_armv7";
        } else if (currentOs.isLinux() && architecture.isArmv8()) {
            return "linux_armv8";
        } else if (currentOs.isMacOsX() && architecture.isAmd64()) { //MAX OS X
            return "osx_x64";
        } else if (currentOs.isMacOsX() && architecture.isI386()) {
            return "osx_x86";
        } else if (currentOs.isSolaris() && architecture.isAmd64()) { //SOLARIS
            return "solaris_x64";
        } else if (currentOs.isSolaris() && architecture.isI386()) {
            return "solaris_x86";
        } else if (currentOs.isSolaris() && architecture.isSparc()) {
            return "solaris_sparc";
        } else if (currentOs.isSolaris() && architecture.isUltraSparc()) {
            return "solaris_ultrasparc";
        } else {
            //TODO freekh: create test case for this
            throw new UnsupportedOperationException("Could not create a default native platform for os: " + currentOs.getName() + " and architecture: " + architecture.getDisplayName());
        }
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
