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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeplatform.registry.WindowsRegistry;
import org.gradle.internal.nativeplatform.registry.WindowsRegistryAccess;
import org.gradle.internal.nativeplatform.registry.WindowsRegistryException;
import org.gradle.internal.os.OperatingSystem;

public class DefaultWindowsSdkLocator extends DefaultWindowsLocator implements WindowsSdkLocator {
    private static final String REGISTRY_ROOTPATH_SDK = "SOFTWARE\\Microsoft\\Microsoft SDKs\\Windows";
    private static final String REGISTRY_ROOTPATH_KIT = "SOFTWARE\\Microsoft\\Windows Kits\\Installed Roots";
    private static final String REGISTRY_CURRENTFOLDER = "CurrentInstallFolder";
    private static final String REGISTRY_CURRENTVERSION = "CurrentVersion";
    private static final String REGISTRY_FOLDER = "InstallationFolder";
    private static final String REGISTRY_VERSION = "ProductVersion";
    private static final String REGISTRY_NAME = "ProductName";
    private static final String REGISTRY_KIT_8 = "KitsRoot";
    private static final String REGISTRY_KIT_81 = "KitsRoot81";
    private static final String VERSION_KIT_8 = "8.0";
    private static final String VERSION_KIT_81 = "8.1";
    private static final String VERSION_PATH = "path";
    private static final String VERSION_USER = "user";

    private static final String NAME_PATH = "Path-resolved Windows SDK";
    private static final String NAME_USER = "User-provided Windows SDK";
    private static final String NAME_SDK = "Windows SDK";
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

    private final Map<String, WindowsSdk> foundSdks = new HashMap<String, WindowsSdk>();
    private final OperatingSystem os;
    private WindowsSdk defaultSdk;

    public DefaultWindowsSdkLocator() {
        this(OperatingSystem.current());
    }

    public DefaultWindowsSdkLocator(OperatingSystem os) {
        this.os = os;
    }

    public boolean locateWindowsSdks(File candidate) {
        if (!foundSdks.isEmpty()) {
            return true;
        }

        Map<File, String> foundPaths = new HashMap<File, String>();

        locateSdksInRegistry(foundPaths);
        locateKitsInRegistry(foundPaths);
        locateSdkInPath(foundPaths);

        if (candidate != null) {
            locateUserSpecifiedSdk(foundPaths, candidate);
        }

        if (defaultSdk == null) {
            determineDefaultSdk(foundPaths);
        }

        return !foundSdks.isEmpty();
    }

    public WindowsSdk getDefaultSdk() {
        return defaultSdk;
    }

    public WindowsSdk getSdk(String version) {
        return foundSdks.get(version);
    }

    private void locateSdksInRegistry(Map<File, String> foundPaths) {
        try {
            WindowsRegistryAccess localMachine = WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE);
            List<String> subkeys = localMachine.getSubkeys(REGISTRY_ROOTPATH_SDK);

            for (String subkey : subkeys) {
                try {
                    String basePath = REGISTRY_ROOTPATH_SDK + "\\" + subkey;
                    File folder = new File(localMachine.readString(basePath, REGISTRY_FOLDER));
                    String version = formatVersion(localMachine.readString(basePath, REGISTRY_VERSION));
                    String name;

                    try {
                        name = localMachine.readString(basePath, REGISTRY_NAME);
                    } catch (WindowsRegistryException e) {
                        name = NAME_SDK + " " + version;
                    }

                    if (isWindowsSdk(folder)) {
                        foundPaths.put(folder, version);
                        addSdk(folder, version, name);
                    }
                } catch (WindowsRegistryException e) {

                }
            }
        } catch (WindowsRegistryException e) {

        }
    }

    private void locateKitsInRegistry(Map<File, String> foundPaths) {
        try {
            WindowsRegistryAccess localMachine = WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE);
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
                    File path = new File(localMachine.readString(REGISTRY_ROOTPATH_KIT, keys[i]));
                    if (isWindowsSdk(path)) {
                        foundPaths.put(path, versions[i]);
                        addSdk(path, versions[i], NAME_KIT + " " + versions[i]);
                    }
                } catch (WindowsRegistryException e) {

                }
            }
        } catch (WindowsRegistryException e) {

        }
    }

    private void locateSdkInPath(Map<File, String> foundPaths) {
        File resourceCompiler = os.findInPath(RESOURCE_FILENAME);
        if (resourceCompiler != null) {
            Search search = locateInHierarchy(resourceCompiler, isWindowsSdk());
            if (search.isFound()) {
                File path = search.getResult();

                if (!foundPaths.containsKey(path)) {
                    foundPaths.put(path, VERSION_PATH);
                    addSdk(path, VERSION_PATH, NAME_PATH);
                }

                if (defaultSdk == null) {
                    defaultSdk = foundSdks.get(VERSION_PATH);
                }
            }
        }
    }

    private void locateUserSpecifiedSdk(Map<File, String> foundPaths, File candidate) {
        Search search = locateInHierarchy(candidate, isWindowsSdk());
        if (search.isFound()) {
            candidate = search.getResult();
            if (!foundPaths.containsKey(candidate)) {
                foundPaths.put(candidate, VERSION_USER);
                addSdk(candidate, VERSION_USER, NAME_USER);
            }

            defaultSdk = foundSdks.get(foundPaths.get(candidate));
        }
    }

    private void determineDefaultSdk(Map<File, String> foundPaths) {
        try {
            WindowsRegistryAccess localMachine = WindowsRegistry.get(WindowsRegistry.HKEY_LOCAL_MACHINE);
            String currentPath = localMachine.readString(REGISTRY_ROOTPATH_SDK, REGISTRY_CURRENTFOLDER);
            String currentVersion = formatVersion(localMachine.readString(REGISTRY_ROOTPATH_SDK, REGISTRY_CURRENTVERSION));
            String foundVersion = foundPaths.get(currentPath);

            if (foundVersion != null) {
                defaultSdk = foundSdks.get(foundVersion);
            } else {
                defaultSdk = foundSdks.get(currentVersion);
            }
        } catch (WindowsRegistryException e) {

        } finally {
            if (defaultSdk == null && !foundSdks.isEmpty()) {
                defaultSdk = foundSdks.entrySet().iterator().next().getValue();
            }
        }

    }

    private void addSdk(File path, String version, String name) {
        foundSdks.put(version, new WindowsSdk(path, version, name));
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
