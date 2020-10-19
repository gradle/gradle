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

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WindowsInstallationSupplier extends AutoDetectingInstallationSupplier {

    private final WindowsRegistry windowsRegistry;
    private final OperatingSystem os;

    public WindowsInstallationSupplier(WindowsRegistry registry, OperatingSystem os, ProviderFactory providerFactory) {
        super(providerFactory);
        this.windowsRegistry = registry;
        this.os = os;
    }

    @Override
    protected Set<InstallationLocation> findCandidates() {
        if (os.isWindows()) {
            return findInstallationsInRegistry();
        }
        return Collections.emptySet();
    }

    private Set<InstallationLocation> findInstallationsInRegistry() {
        final Stream<String> openJdkInstallations = findAdoptOpenJdk().stream();
        final Stream<String> jvms = Lists.newArrayList(
            "SOFTWARE\\JavaSoft\\JDK",
            "SOFTWARE\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\JavaSoft\\Java Runtime Environment",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit",
            "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment"
        ).stream().map(this::findJvms).flatMap(List::stream);
        return Stream.concat(openJdkInstallations, jvms)
            .map(javaHome -> new InstallationLocation(new File(javaHome), "Windows Registry"))
            .collect(Collectors.toSet());
    }

    private List<String> find(String sdkSubkey, String path, String value) {
        try {
            return getVersions(sdkSubkey).stream()
                .map(version -> getValue(sdkSubkey, path, value, version)).collect(Collectors.toList());
        } catch (MissingRegistryEntryException e) {
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

    private List<String> findAdoptOpenJdk() {
        return find("SOFTWARE\\AdoptOpenJDK\\JDK", "\\hotspot\\MSI", "Path");
    }

    private List<String> findJvms(String sdkSubkey) {
        return find(sdkSubkey, "", "JavaHome");
    }

}
