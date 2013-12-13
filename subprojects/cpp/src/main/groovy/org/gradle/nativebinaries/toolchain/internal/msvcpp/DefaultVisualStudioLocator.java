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

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;

import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.toolchain.internal.ToolSearchResult;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultVisualStudioLocator extends DefaultWindowsLocator implements VisualStudioLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisualStudioLocator.class);
    private static final String[] REGISTRY_BASEPATHS = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH = "Microsoft\\VisualStudio\\SxS\\VS7";
    private static final String COMPILER_PATH = "VC/bin/";
    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String VERSION_PATH = "path";
    private static final String VERSION_USER = "user";

    private static final String VISUAL_STUDIO_DISPLAY_NAME = "Visual Studio installation";
    private static final String NAME_VISUALSTUDIO = "Visual Studio";
    private static final String NAME_PATH = "Path-resolved Visual Studio";
    private static final String NAME_USER = "User-provided Visual Studio";

    private final Map<File, VisualStudioInstall> foundInstalls = new HashMap<File, VisualStudioInstall>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private VisualStudioInstall pathInstall;
    private VisualStudioInstall userInstall;
    private VisualStudioInstall defaultInstall;
    private ToolSearchResult result;

    public DefaultVisualStudioLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
    }

    public ToolSearchResult locateVisualStudioInstalls(File candidate) {
        if (result != null) {
            return result;
        }

        locateInstallsInRegistry();
        locateInstallInPath();

        if (candidate != null) {
            locateUserSpecifiedInstall(candidate);
        }

        defaultInstall = determineDefaultInstall();

        result = new ToolSearchResult() {

            public boolean isAvailable() {
                return defaultInstall != null;
            }

            public void explain(TreeVisitor<? super String> visitor) {
                visitor.node(String.format("%s could not be found.", VISUAL_STUDIO_DISPLAY_NAME));
            }

        };

        return result;
    }

    public VisualStudioInstall getDefaultInstall() {
        return defaultInstall;
    }

    private void locateInstallsInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateInstallsInRegistry(baseKey);
        }
    }

    private void locateInstallsInRegistry(String baseKey) {
        try {
            List<String> valueNames = windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH);

            for (String valueName : valueNames) {
                File installDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH, valueName));

                if (isVisualStudio(installDir)) {
                    LOGGER.debug("Found Visual Studio {} at {}", valueName, installDir);
                    addInstall(installDir, valueName, NAME_VISUALSTUDIO + " " + valueName);
                } else {
                    LOGGER.debug("Ignoring candidate Visual Studio directory {} as it does not look like a Visual Studio installation.", installDir);
                }
            }
        } catch (MissingRegistryEntryException e) {
            // No Visual Studio information available in the registry
        }
    }

    private void locateInstallInPath() {
        File compilerInPath = os.findInPath(COMPILER_FILENAME);
        if (compilerInPath != null) {
            Search search = locateInHierarchy(VISUAL_STUDIO_DISPLAY_NAME, compilerInPath, isVisualStudio());
            if (search.isAvailable()) {
                File installDir = search.getResult();
                LOGGER.debug("Found Visual Studio install {} using system path", installDir);
                if (!foundInstalls.containsKey(installDir)) {
                    addInstall(installDir, VERSION_PATH, NAME_PATH);
                }
                pathInstall = foundInstalls.get(installDir);
            } else {
                LOGGER.debug("Ignoring candidate Visual Studio install for {} as it does not look like a Visual Studio installation.", compilerInPath);
            }
        }
    }

    private void locateUserSpecifiedInstall(File candidate) {
        Search search = locateInHierarchy(VISUAL_STUDIO_DISPLAY_NAME, candidate, isVisualStudio());
        if (search.isAvailable()) {
            candidate = search.getResult();
            LOGGER.debug("Found Visual Studio install {} using configured path", candidate);
            if (!foundInstalls.containsKey(candidate)) {
                addInstall(candidate, VERSION_USER, NAME_USER);
            }
            userInstall = foundInstalls.get(candidate);
        }
    }

    private void addInstall(File path, String version, String name) {
        // TODO: MPU - analyze install to detect available (cross-)compilers and pass the complete information to VisualStudioInstall
        foundInstalls.put(path, new VisualStudioInstall(path, VersionNumber.parse(version), name));
    }

    private VisualStudioInstall determineDefaultInstall() {
        if (userInstall != null) {
            return userInstall;
        }
        if (pathInstall != null) {
            return pathInstall;
        }

        VisualStudioInstall candidate = null;

        for (VisualStudioInstall visualStudio : foundInstalls.values()) {
            if (candidate == null || visualStudio.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = visualStudio;
            }
        }

        return candidate;
    }

    private static Spec<File> isVisualStudio() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return isVisualStudio(element);
            }
        };
    }

    private static boolean isVisualStudio(File candidate) {
        return new File(candidate, COMPILER_PATH + COMPILER_FILENAME).isFile();
    }
}
