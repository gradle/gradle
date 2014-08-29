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

import net.rubygrapefruit.platform.MissingRegistryEntryException;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.ArchitectureNotationParser;
import org.gradle.util.GFileUtils;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class DefaultVisualStudioLocator implements VisualStudioLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisualStudioLocator.class);
    private static final String[] REGISTRY_BASEPATHS = {
        "SOFTWARE\\",
        "SOFTWARE\\Wow6432Node\\"
    };
    private static final String REGISTRY_ROOTPATH_VC = "Microsoft\\VisualStudio\\SxS\\VC7";
    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_COMMONTOOLS = PATH_COMMON + "Tools/";
    private static final String PATH_COMMONIDE = PATH_COMMON + "IDE/";
    private static final String PATH_BIN = "bin/";
    private static final String PATH_INCLUDE = "include/";
    private static final String COMPILER_FILENAME = "cl.exe";

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

    private final Map<File, VisualStudioInstall> foundInstalls = new HashMap<File, VisualStudioInstall>();
    private final OperatingSystem os;
    private final WindowsRegistry windowsRegistry;
    private final SystemInfo systemInfo;
    private VisualStudioInstall pathInstall;
    private boolean initialised;

    public DefaultVisualStudioLocator(OperatingSystem os, WindowsRegistry windowsRegistry, SystemInfo systemInfo) {
        this.os = os;
        this.windowsRegistry = windowsRegistry;
        this.systemInfo = systemInfo;
    }

    public SearchResult locateVisualStudioInstalls(File candidate) {
        if (!initialised) {
            locateInstallsInRegistry();
            locateInstallInPath();
            initialised = true;
        }

        if (candidate != null) {
            return locateUserSpecifiedInstall(candidate);
        }

        return determineDefaultInstall();
    }

    private void locateInstallsInRegistry() {
        for (String baseKey : REGISTRY_BASEPATHS) {
            locateInstallsInRegistry(baseKey);
        }
    }

    private void locateInstallsInRegistry(String baseKey) {
        List<String> visualCppVersions;
        try {
            visualCppVersions = windowsRegistry.getValueNames(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC);
        } catch (MissingRegistryEntryException e) {
            // No Visual Studio information available in the registry
            return;
        }

        for (String valueName : visualCppVersions) {
            if (!valueName.matches("\\d+\\.\\d+")) {
                // Ignore the other values
                continue;
            }
            File visualCppDir = new File(windowsRegistry.getStringValue(WindowsRegistry.Key.HKEY_LOCAL_MACHINE, baseKey + REGISTRY_ROOTPATH_VC, valueName));
            visualCppDir = GFileUtils.canonicalise(visualCppDir);
            File visualStudioDir = visualCppDir.getParentFile();

            if (isVisualCpp(visualCppDir) && isVisualStudio(visualStudioDir)) {
                LOGGER.debug("Found Visual C++ {} at {}", valueName, visualCppDir);
                VersionNumber version = VersionNumber.parse(valueName);
                VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ " + valueName, visualStudioDir, visualCppDir, version);
                VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
                foundInstalls.put(visualStudioDir, visualStudio);
            } else {
                LOGGER.debug("Ignoring candidate Visual C++ directory {} as it does not look like a Visual C++ installation.", visualCppDir);
            }
        }
    }

    private void locateInstallInPath() {
        File compilerInPath = os.findInPath(COMPILER_FILENAME);
        if (compilerInPath == null) {
            LOGGER.debug("No visual c++ compiler found in system path.");
            return;
        }

        File visualCppDir = GFileUtils.canonicalise(compilerInPath.getParentFile().getParentFile());
        if (!isVisualCpp(visualCppDir)) {
            visualCppDir = visualCppDir.getParentFile();
            if (!isVisualCpp(visualCppDir)) {
                LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", compilerInPath);
                return;
            }
        }
        LOGGER.debug("Found Visual C++ install {} using system path", visualCppDir);

        File visualStudioDir = visualCppDir.getParentFile();
        if (!foundInstalls.containsKey(visualStudioDir)) {
            VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ from system path", visualStudioDir, visualCppDir, VersionNumber.UNKNOWN);
            VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
            foundInstalls.put(visualStudioDir, visualStudio);
        }
        pathInstall = foundInstalls.get(visualStudioDir);
    }

    private SearchResult locateUserSpecifiedInstall(File candidate) {
        File visualStudioDir = GFileUtils.canonicalise(candidate);
        File visualCppDir = new File(visualStudioDir, "VC");
        if (!isVisualStudio(visualStudioDir) || !isVisualCpp(visualCppDir)) {
            LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", candidate);
            return new InstallNotFound(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate));
        }

        if (!foundInstalls.containsKey(visualStudioDir)) {
            VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ from user provided path", visualStudioDir, visualCppDir, VersionNumber.UNKNOWN);
            VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
            foundInstalls.put(visualStudioDir, visualStudio);
        }
        return new InstallFound(foundInstalls.get(visualStudioDir));
    }

    private VisualCppInstall buildVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version) {
        boolean isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64;
        Map<Architecture, List<File>> paths = new HashMap<Architecture, List<File>>();
        Map<Architecture, File> binaryPaths = new HashMap<Architecture, File>();
        Map<Architecture, File> libraryPaths = new HashMap<Architecture, File>();
        Map<Architecture, File> includePaths = new HashMap<Architecture, File>();
        Map<Architecture, String> assemblerFilenames = new HashMap<Architecture, String>();
        Map<Architecture, Map<String, String>> definitions = new HashMap<Architecture, Map<String, String>>();
        NotationParser<Object, ArchitectureInternal> architectureParser = ArchitectureNotationParser.parser();
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

        // TODO:MPUT - use x64 as the default architecture on x64 systems (same for winsdk)? (isNativeAmd64 && binaryPaths.containsKey(amd64)) ? amd64 : x86
        return new VisualCppInstall(name, version, x86, paths, binaryPaths, libraryPaths, includePaths, assemblerFilenames, definitions);
    }

    private SearchResult determineDefaultInstall() {
        if (pathInstall != null) {
            return new InstallFound(pathInstall);
        }

        VisualStudioInstall candidate = null;

        for (VisualStudioInstall visualStudio : foundInstalls.values()) {
            if (candidate == null || visualStudio.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = visualStudio;
            }
        }

        return candidate == null ? new InstallNotFound("Could not locate a Visual Studio installation, using the Windows registry and system path.") : new InstallFound(candidate);
    }

    private static boolean isVisualStudio(File candidate) {
        return new File(candidate, PATH_COMMON).isDirectory() && isVisualCpp(new File(candidate, "VC"));
    }

    private static boolean isVisualCpp(File candidate) {
        return new File(candidate, PATH_BIN + COMPILER_FILENAME).isFile();
    }

    private static class InstallFound implements SearchResult {
        private final VisualStudioInstall install;

        public InstallFound(VisualStudioInstall install) {
            this.install = install;
        }

        public VisualStudioInstall getVisualStudio() {
            return install;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class InstallNotFound implements SearchResult {
        private final String message;

        private InstallNotFound(String message) {
            this.message = message;
        }

        public VisualStudioInstall getVisualStudio() {
            return null;
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }
}
