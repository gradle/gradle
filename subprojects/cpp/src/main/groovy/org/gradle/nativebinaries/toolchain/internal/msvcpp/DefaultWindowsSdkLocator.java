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
package org.gradle.nativebinaries.toolchain.internal.msvcpp;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultWindowsSdkLocator extends DefaultWindowsLocator implements WindowsSdkLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWindowsSdkLocator.class);
    private static final String REGISTRY_BASEPATHS[] = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH_SDK = "Microsoft\\Microsoft SDKs\\Windows";
    private static final String REGISTRY_ROOTPATH_KIT = "Microsoft\\Windows Kits\\Installed Roots";
    private static final String REGISTRY_FOLDER = "InstallationFolder";
    private static final String REGISTRY_VERSION = "ProductVersion";
    private static final String REGISTRY_NAME = "ProductName";
    private static final String REGISTRY_KIT_8 = "KitsRoot";
    private static final String REGISTRY_KIT_81 = "KitsRoot81";
    private static final String VERSION_KIT_8 = "8.0";
    private static final String VERSION_KIT_81 = "8.1";
    private static final String VERSION_PATH = "path";
    private static final String VERSION_USER = "user";

    private static final String SDK_DISPLAY_NAME = "Windows SDK";
    private static final String NAME_PATH = "Path-resolved Windows SDK";
    private static final String NAME_USER = "User-provided Windows SDK";
    private static final String NAME_KIT = "Windows Kit";

    private static final String RESOURCE_PATHS[] = {
        "bin/x86/",
        "bin/"
    };

    private static final String KERNEL32_PATHS[] = {
        "lib/winv6.3/um/x86/",
        "lib/win8/um/x86/",
        "lib/"
    };

    private static final String RESOURCE_FILENAME = "rc.exe";
    private static final String KERNEL32_FILENAME = "kernel32.lib";

    private final Map<File, WindowsSdk> foundSdks = new HashMap<File, WindowsSdk>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private WindowsSdk pathSdk;
    private WindowsSdk userSdk;
    private WindowsSdk defaultSdk;
    private ToolSearchResult result;

    public DefaultWindowsSdkLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
    }

    public ToolSearchResult locateWindowsSdks(File candidate) {
        if (result != null) {
            return result;
        }

        locateSdksInRegistry();
        locateKitsInRegistry();
        locateSdkInPath();

        if (candidate != null) {
            locateUserSpecifiedSdk(candidate);
        }

        defaultSdk = determineDefaultSdk();

        result = new ToolSearchResult() {
            public boolean isAvailable() {
                return defaultSdk != null;
            }

            public void explain(TreeVisitor<? super String> visitor) {
                visitor.node(String.format("%s could not be found.", SDK_DISPLAY_NAME));
            }
        };

        return result;
    }

    public WindowsSdk getDefaultSdk() {
        return defaultSdk;
    }

    private void locateSdksInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateSdksInRegistry(baseKey);
        }
    }

    private void locateSdksInRegistry(String baseKey) {
        try {
            List<String> subkeys = windowsRegistry.getSubkeys(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_SDK);

            for (String subkey : subkeys) {
                try {
                    String basePath = baseKey + REGISTRY_ROOTPATH_SDK + "\\" + subkey;
                    File sdkDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_FOLDER));
                    String version = formatVersion(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_VERSION));
                    String name = windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_NAME);

                    if (isWindowsSdk(sdkDir)) {
                        LOGGER.debug("Found Windows SDK {} at {}", version, sdkDir);
                        addSdk(sdkDir, version, name);
                    } else {
                        LOGGER.debug("Ignoring candidate Windows SDK directory {} as it does not look like a Windows SDK installation.", sdkDir);
                    }
                } catch (MissingRegistryEntryException e) {
                    // Ignore the subkey if it doesn't have a folder and version
                }
            }
        } catch (MissingRegistryEntryException e) {
            // No SDK information available in the registry
        }
    }

    private void locateKitsInRegistry() {
        String[] versions = {
                VERSION_KIT_8,
                VERSION_KIT_81
        };
        String[] keys = {
                REGISTRY_KIT_8,
                REGISTRY_KIT_81
        };

        for (int i = 0; i != keys.length; ++i) {
            try {
                File kitDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, REGISTRY_ROOTPATH_KIT, keys[i]));
                if (isWindowsSdk(kitDir)) {
                    LOGGER.debug("Found Windows Kit {} at {}", versions[i], kitDir);
                    addSdk(kitDir, versions[i], NAME_KIT + " " + versions[i]);
                } else {
                    LOGGER.debug("Ignoring candidate Windows Kit directory {} as it does not look like a Windows Kit installation.", kitDir);
                }
            } catch (MissingRegistryEntryException e) {
                // Ignore the version if the string cannot be read
            }
        }
    }

    private void locateSdkInPath() {
        File resourceCompiler = os.findInPath(RESOURCE_FILENAME);
        if (resourceCompiler != null) {
            Search search = locateInHierarchy(SDK_DISPLAY_NAME, resourceCompiler, isWindowsSdk());
            if (search.isAvailable()) {
                File sdkDir = search.getResult();
                LOGGER.debug("Found Windows SDK {} using system path", sdkDir);
                if (!foundSdks.containsKey(sdkDir)) {
                    addSdk(sdkDir, VERSION_PATH, NAME_PATH);
                }
                pathSdk = foundSdks.get(sdkDir);
            } else {
                LOGGER.debug("Ignoring candidate Windows SDK for {} as it does not look like a Windows SDK installation.", resourceCompiler);
            }
        }
    }

    private void locateUserSpecifiedSdk(File candidate) {
        Search search = locateInHierarchy(SDK_DISPLAY_NAME, candidate, isWindowsSdk());
        if (search.isAvailable()) {
            candidate = search.getResult();
            LOGGER.debug("Found Windows SDK {} using configured path", candidate);
            if (!foundSdks.containsKey(candidate)) {
                addSdk(candidate, VERSION_USER, NAME_USER);
            }
            userSdk = foundSdks.get(candidate);
        }
    }

    private WindowsSdk determineDefaultSdk() {
        if (userSdk != null) {
            return userSdk;
        }
        if (pathSdk != null) {
            return pathSdk;
        }

        WindowsSdk candidate = null;
        for (WindowsSdk windowsSdk : foundSdks.values()) {
            if (candidate == null || windowsSdk.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = windowsSdk;
            }
        }
        return candidate;
    }

    private void addSdk(File path, String version, String name) {
        foundSdks.put(path, new WindowsSdk(path, VersionNumber.parse(version), name));
    }

    private static Spec<File> isWindowsSdk() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return isWindowsSdk(element);
            }
        };
    }

    private static boolean isWindowsSdk(File candidate) {
        boolean hasResourceCompiler = false;
        boolean hasKernel32Lib = false;

        for (String path : RESOURCE_PATHS) {
            if (new File(candidate, path + RESOURCE_FILENAME).isFile()) {
                hasResourceCompiler = true;
                break;
            }
        }

        for (String path : KERNEL32_PATHS) {
            if (new File(candidate, path + KERNEL32_FILENAME).isFile()) {
                hasKernel32Lib = true;
                break;
            }
        }

        return hasResourceCompiler && hasKernel32Lib;
    }

    private static String formatVersion(String version) {
        int index = StringUtils.ordinalIndexOf(version, ".", 2);

        if (index != -1) {
            version = version.substring(0, index);
        }

        return version;
    }

}
