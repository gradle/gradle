/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jvm;

import com.google.common.collect.ImmutableSet;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

import static net.rubygrapefruit.platform.WindowsRegistry.Key.HKEY_LOCAL_MACHINE;

public class WindowsJavaInstallationsDirLocatorStrategy implements JavaInstallationsDirLocatorStrategy {

    private static final String[] CONVENTIONAL_REGISTRY_NODES = new String[]{
        "SOFTWARE\\JavaSoft\\Java Development Kit",
        "SOFTWARE\\JavaSoft\\Java Runtime Environment",
        "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Development Kit",
        "SOFTWARE\\Wow6432Node\\JavaSoft\\Java Runtime Environment"
    };

    private final WindowsRegistry windowsRegistry;

    public WindowsJavaInstallationsDirLocatorStrategy(WindowsRegistry windowsRegistry) {
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    public Set<File> findJavaInstallationsDirs() {
        ImmutableSet.Builder<File> builder = ImmutableSet.<File>builder();
        for (String registryNode : CONVENTIONAL_REGISTRY_NODES) {
            List<String> versions;
            try {
                versions = windowsRegistry.getSubkeys(HKEY_LOCAL_MACHINE, registryNode);
            } catch (MissingRegistryEntryException e) {
                // Ignore
                continue;
            }
            for (String version : versions) {
                if (version.matches("\\d+\\.\\d+")) {
                    continue;
                }
                String versionRegistryNode = registryNode + '\\' + version;
                try {
                    String javaHome = windowsRegistry.getStringValue(HKEY_LOCAL_MACHINE, versionRegistryNode, "JavaHome");
                    if (StringUtils.isNotEmpty(javaHome)) {
                        File javaHomeFile = new File(javaHome);
                        if (javaHomeFile.isDirectory()) {
                            builder.add(javaHomeFile);
                        }
                    }
                } catch (MissingRegistryEntryException e) {
                    // Ignore
                }
            }
        }
        return builder.build();
    }
}
