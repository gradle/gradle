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
package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.apache.commons.lang.StringUtils;
import org.gradle.internal.FileUtils;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.platform.base.internal.toolchain.ComponentFound;
import org.gradle.platform.base.internal.toolchain.ComponentNotFound;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.util.internal.VersionNumber;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//TODO: Simplify this class by busting it up into a locator for legacy SDKs and locator(s) for Windows 8 kits
public class LegacyWindowsSdkLocator implements WindowsSdkLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyWindowsSdkLocator.class);
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
    private static final String VERSION_USER = "user";

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

    private final Map<File, WindowsSdkInstall> foundSdks = new HashMap<File, WindowsSdkInstall>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private WindowsSdkInstall pathSdk;
    private boolean initialised;

    public LegacyWindowsSdkLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    public SearchResult<WindowsSdkInstall> locateComponent(@Nullable File candidate) {
        initializeWindowsSdks();

        if (candidate != null) {
            return locateUserSpecifiedSdk(candidate);
        }

        return locateDefaultSdk();
    }

    @Override
    public List<? extends WindowsSdkInstall> locateAllComponents() {
        initializeWindowsSdks();
        return Lists.newArrayList(foundSdks.values());
    }

    private void initializeWindowsSdks() {
        if (!initialised) {
            locateSdksInRegistry();
            locateKitsInRegistry();
            locateSdkInPath();
            initialised = true;
        }
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
                    File sdkDir = FileUtils.canonicalize(new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, basePath, REGISTRY_FOLDER)));
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
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateKitsInRegistry(baseKey);
        }
    }

    private void locateKitsInRegistry(String baseKey) {
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
                File kitDir = FileUtils.canonicalize(new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_KIT, keys[i])));
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
        if (resourceCompiler == null) {
            LOGGER.debug("Could not find Windows resource compiler in system path.");
            return;
        }
        File sdkDir = FileUtils.canonicalize(resourceCompiler.getParentFile().getParentFile());
        if (!isWindowsSdk(sdkDir)) {
            sdkDir = sdkDir.getParentFile();
            if (!isWindowsSdk(sdkDir)) {
                LOGGER.debug("Ignoring candidate Windows SDK for {} as it does not look like a Windows SDK installation.", resourceCompiler);
            }
        }
        LOGGER.debug("Found Windows SDK {} using system path", sdkDir);

        if (!foundSdks.containsKey(sdkDir)) {
            addSdk(sdkDir, "path", "Path-resolved Windows SDK");
        }
        pathSdk = foundSdks.get(sdkDir);
    }

    private SearchResult<WindowsSdkInstall> locateUserSpecifiedSdk(File candidate) {
        File sdkDir = FileUtils.canonicalize(candidate);
        if (!isWindowsSdk(sdkDir)) {
            return new ComponentNotFound<WindowsSdkInstall>(String.format("The specified installation directory '%s' does not appear to contain a Windows SDK installation.", candidate));
        }

        if (!foundSdks.containsKey(sdkDir)) {
            addSdk(sdkDir, VERSION_USER, NAME_USER);
        }
        return new ComponentFound<WindowsSdkInstall>(foundSdks.get(sdkDir));
    }

    private SearchResult<WindowsSdkInstall> locateDefaultSdk() {
        if (pathSdk != null) {
            return new ComponentFound<WindowsSdkInstall>(pathSdk);
        }

        WindowsSdkInstall candidate = null;
        for (WindowsSdkInstall windowsSdk : foundSdks.values()) {
            if (candidate == null || windowsSdk.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = windowsSdk;
            }
        }
        return candidate == null ? new ComponentNotFound<WindowsSdkInstall>("Could not locate a Windows SDK installation, using the Windows registry and system path.") : new ComponentFound<WindowsSdkInstall>(candidate);
    }

    private void addSdk(File path, String version, String name) {
        foundSdks.put(path, new LegacyWindowsSdkInstall(path, VersionNumber.parse(version), name));
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
