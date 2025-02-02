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

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.nativeintegration.NativeIntegrationUnavailableException;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WindowsInstallationSupplier implements InstallationSupplier {

    private final WindowsRegistry windowsRegistry;
    private final OperatingSystem os;

    public WindowsInstallationSupplier(WindowsRegistry registry, OperatingSystem os) {
        this.windowsRegistry = registry;
        this.os = os;
    }

    @Override
    public String getSourceName() {
        return "Windows Registry";
    }

    @Override
    public Set<InstallationLocation> get() {
        if (os.isWindows()) {
            return findInstallationsInRegistry();
        }
        return Collections.emptySet();
    }

    private Set<InstallationLocation> findInstallationsInRegistry() {
        final Stream<String> openJdkInstallations = findOpenJDKs();
        final Stream<String> jvms = Stream.of(
            "SOFTWARE\\JavaSoft\\JDK",
            "SOFTWARE\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\JavaSoft\\Java Runtime Environment",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment"
        ).map(this::findJvms).flatMap(List::stream);
        return Stream.concat(openJdkInstallations, jvms)
            .map(javaHome -> InstallationLocation.autoDetected(new File(javaHome), getSourceName()))
            .collect(Collectors.toSet());
    }

    private List<String> find(String sdkSubkey, String path, String value) {
        try {
            List<String> versions = getVersions(sdkSubkey);
            return versions.stream().map(version -> getValue(sdkSubkey, path, value, version)).collect(Collectors.toList());
        } catch (MissingRegistryEntryException | NativeIntegrationUnavailableException e) {
            // Ignore
            return Collections.emptyList();
        }
    }

    private List<String> getVersions(String sdkSubkey) {
        return windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey);
    }

    private String getValue(String sdkSubkey, String path, String value, String version) {
        return windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, sdkSubkey + '\\' + version + path, value);
    }

    private Stream<String> findOpenJDKs() {
        return Stream.of(
            "SOFTWARE\\AdoptOpenJDK\\JDK",
            "SOFTWARE\\Eclipse Adoptium\\JDK",
            "SOFTWARE\\Eclipse Foundation\\JDK"
        ).flatMap(key -> find(key, "\\hotspot\\MSI", "Path").stream());
    }

    private List<String> findJvms(String sdkSubkey) {
        return find(sdkSubkey, "", "JavaHome");
    }

}
