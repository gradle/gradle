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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.WindowsRegistry;

import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativebinaries.platform.Architecture;
import org.gradle.nativebinaries.platform.internal.ArchitectureNotationParser;
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
    private static final String REGISTRY_ROOTPATH_VS = "Microsoft\\VisualStudio\\SxS\\VS7";
    private static final String REGISTRY_ROOTPATH_VC = "Microsoft\\VisualStudio\\SxS\\VC7";
    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_COMMONTOOLS = PATH_COMMON + "Tools/";
    private static final String PATH_COMMONIDE = PATH_COMMON + "IDE/";
    private static final String PATH_BIN = "bin/";
    private static final String PATH_INCLUDE = "include/";
    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String VERSION_PATH = "path";
    private static final String VERSION_USER = "user";

    private static final String NATIVEPREFIX_AMD64 = "win32-amd64";
    private static final String ARCHITECTURE_AMD64 = "amd64";
    private static final String ARCHITECTURE_X86 = "x86";
    private static final String ARCHITECTURE_ARM = "arm";
    private static final String ARCHITECTURE_IA64 = "ia-64";
    private static final String BINPATH_AMD64_AMD64 = "bin/amd64";
    private static final String BINPATH_AMD64_ARM = "bin/amd64_arm";
    private static final String BINPATH_AMD64_X86 = "bin/amd64_x86";
    private static final String BINPATH_X86_AMD64 = "bin/x86_amd64";
    private static final String BINPATH_X86_ARM = "bin/x86_arm";
    private static final String BINPATH_X86_IA64 = "bin/x86_ia64";
    private static final String BINPATH_X86_X86 = "bin";
    private static final String LIBPATH_AMD64 = "lib/amd64";
    private static final String LIBPATH_ARM = "lib/arm";
    private static final String LIBPATH_IA64 = "lib/ia64";
    private static final String LIBPATH_X86 = "lib";
    private static final String ASSEMBLER_FILENAME_AMD64 = "ml64.exe";
    private static final String ASSEMBLER_FILENAME_ARM = "armasm.exe";
    private static final String ASSEMBLER_FILENAME_IA64 = "ias.exe";
    private static final String ASSEMBLER_FILENAME_X86 = "ml.exe";
    private static final String DEFINE_ARMPARTITIONAVAILABLE = "_ARM_WINAPI_PARTITION_DESKTOP_SDK_AVAILABLE";

    private static final String VISUAL_STUDIO_DISPLAY_NAME = "Visual Studio installation";
    private static final String VISUAL_CPP_DISPLAY_NAME = "Visual C++ installation";
    private static final String NAME_VISUALSTUDIO = "Visual Studio";
    private static final String NAME_VISUALCPP = "Visual C++";
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
            List<String> valueNames = windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VS);

            for (String valueName : valueNames) {
                File installDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VS, valueName));

                if (isVisualStudio(installDir)) {
                    LOGGER.debug("Found Visual Studio {} at {}", valueName, installDir);
                    addVisualStudioInstall(installDir, valueName, NAME_VISUALSTUDIO + " " + valueName);
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
                    addVisualStudioInstall(installDir, VERSION_PATH, NAME_PATH);
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
                addVisualStudioInstall(candidate, VERSION_USER, NAME_USER);
            }
            userInstall = foundInstalls.get(candidate);
        }
    }

    private void addVisualStudioInstall(File path, String version, String name) {
        File visualCppDir = locateVisualCpp(path, version);
        VersionNumber versionNumber = VersionNumber.parse(version);
        VisualCppInstall visualCpp = buildVisualCppInstall(path, visualCppDir, versionNumber);
        VisualStudioInstall install = new VisualStudioInstall(path, versionNumber, name, visualCpp);
        foundInstalls.put(path, install);
    }

    private File locateVisualCpp(File path, String version) {
        File visualCppDir = null;

        for (String baseKey : REGISTRY_BASEPATHS) {
            visualCppDir = locateVisualCpp(path, version, baseKey);
            if (visualCppDir != null) {
                return visualCppDir;
            }
        }

        Search search = locateInHierarchy(VISUAL_CPP_DISPLAY_NAME, path, isVisualCpp());
        if (search.isAvailable()) {
            visualCppDir = search.getResult();
            LOGGER.debug("Found Visual C++ install {} within Visual Studio install {}", visualCppDir, path);
        }

        return visualCppDir;
    }

    private File locateVisualCpp(File path, String version, String baseKey) {
        try {
            File visualCppDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, version));

            if (isVisualCpp(visualCppDir)) {
                LOGGER.debug("Found Visual C++ {} at {}", version, visualCppDir);
                return visualCppDir;
            } else {
                LOGGER.debug("Ignoring candidate Visual C++ directory {} as it does not look like a Visual C++ installation.", visualCppDir);
            }
        } catch (MissingRegistryEntryException e) {
            // No Visual C++ information available in the registry
        }

        return null;
    }

    private VisualCppInstall buildVisualCppInstall(File vsPath, File basePath, VersionNumber version) {
        boolean isNativeAmd64 = NATIVEPREFIX_AMD64.equals(org.gradle.internal.os.OperatingSystem.current().getNativePrefix());
        Map<Architecture, List<File>> paths = new HashMap<Architecture, List<File>>();
        Map<Architecture, File> binaryPaths = new HashMap<Architecture, File>();
        Map<Architecture, File> libraryPaths = new HashMap<Architecture, File>();
        Map<Architecture, File> includePaths = new HashMap<Architecture, File>();
        Map<Architecture, String> assemblerFilenames = new HashMap<Architecture, String>();
        Map<Architecture, Map<String, String>> definitions = new HashMap<Architecture, Map<String, String>>();
        NotationParser<Object, Architecture> architectureParser = ArchitectureNotationParser.parser();
        Architecture amd64 = architectureParser.parseNotation(ARCHITECTURE_AMD64);
        Architecture x86 = architectureParser.parseNotation(ARCHITECTURE_X86);
        Architecture arm = architectureParser.parseNotation(ARCHITECTURE_ARM);
        Architecture ia64 = architectureParser.parseNotation(ARCHITECTURE_IA64);
        File includePath = new File(basePath, PATH_INCLUDE);
        File commonTools = new File(vsPath, PATH_COMMONTOOLS);
        File commonIde = new File(vsPath, PATH_COMMONIDE);

        if (isNativeAmd64) {
            Architecture[] architectures = {
                amd64,
                x86,
                arm
            };
            String[] binPaths = {
                BINPATH_AMD64_AMD64,
                BINPATH_AMD64_X86,
                BINPATH_AMD64_ARM
            };
            String[] libPaths = {
                LIBPATH_AMD64,
                LIBPATH_X86,
                LIBPATH_ARM
            };
            String[] asmFilenames = {
                ASSEMBLER_FILENAME_AMD64,
                ASSEMBLER_FILENAME_X86,
                ASSEMBLER_FILENAME_ARM
            };

            for (int i = 0; i != architectures.length; ++i) {
                Architecture architecture = architectures[i];
                File binPath = new File(basePath, binPaths[i]);
                File libPath = new File(basePath, libPaths[i]);

                if (binPath.isDirectory() && libPath.isDirectory()) {
                    Map<String, String> definitionsList = new LinkedHashMap<String, String>();
                    List<File> pathsList = new ArrayList<File>();

                    pathsList.add(commonTools);
                    pathsList.add(commonIde);

                    // For cross-compilers, add the native compiler to the path as well
                    if (architecture != amd64) {
                        pathsList.add(new File(basePath, binPaths[0]));
                    }

                    if (architecture == arm) {
                        definitionsList.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
                    }

                    binaryPaths.put(architecture, binPath);
                    libraryPaths.put(architecture, libPath);
                    includePaths.put(architecture, includePath);
                    assemblerFilenames.put(architecture, asmFilenames[i]);
                    paths.put(architecture, pathsList);
                    definitions.put(architecture, definitionsList);
                }
            }
        }

        Architecture[] architectures = {
            x86,
            amd64,
            ia64,
            arm
        };
        String[] binPaths = {
            BINPATH_X86_X86,
            BINPATH_X86_AMD64,
            BINPATH_X86_IA64,
            BINPATH_X86_ARM
        };
        String[] libPaths = {
            LIBPATH_X86,
            LIBPATH_AMD64,
            LIBPATH_IA64,
            LIBPATH_ARM
        };
        String[] asmFilenames = {
            ASSEMBLER_FILENAME_X86,
            ASSEMBLER_FILENAME_AMD64,
            ASSEMBLER_FILENAME_IA64,
            ASSEMBLER_FILENAME_ARM
        };

        for (int i = 0; i != architectures.length; ++i) {
            Architecture architecture = architectures[i];

            if (!binaryPaths.containsKey(architecture)) {
                File binPath = new File(basePath, binPaths[i]);
                File libPath = new File(basePath, libPaths[i]);
    
                if (binPath.isDirectory() && libPath.isDirectory()) {
                    Map<String, String> definitionsList = new LinkedHashMap<String, String>();
                    List<File> pathsList = new ArrayList<File>();

                    pathsList.add(commonTools);
                    pathsList.add(commonIde);

                    // For cross-compilers, add the native compiler to the path as well
                    if (architecture != x86) {
                        pathsList.add(new File(basePath, binPaths[0]));
                    }

                    if (architecture == arm) {
                        definitionsList.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
                    }

                    binaryPaths.put(architecture, binPath);
                    libraryPaths.put(architecture, libPath);
                    includePaths.put(architecture, includePath);
                    assemblerFilenames.put(architecture, asmFilenames[i]);
                    paths.put(architecture, pathsList);
                    definitions.put(architecture, definitionsList);
                }
            }
        }

        if (binaryPaths.isEmpty()) {
            return null;
        }

        // TODO:MPUT - use x64 as the default architecture on x64 systems (same for winsdk)? (isNativeAmd64 && binaryPaths.containsKey(amd64)) ? amd64 : x86
        return new VisualCppInstall(NAME_VISUALCPP + " " + version, version, x86,
                paths, binaryPaths, libraryPaths, includePaths, assemblerFilenames, definitions);
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
        return new File(candidate, PATH_COMMON).isDirectory();
    }

    private static Spec<File> isVisualCpp() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return isVisualCpp(element);
            }
        };
    }

    private static boolean isVisualCpp(File candidate) {
        return new File(candidate, PATH_BIN + COMPILER_FILENAME).isFile();
    }

}
