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

import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.ArchitectureInternal;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

// TODO:DAZ Move any detection of available tools to the VisualStudioLocator: this class should be constructed with knowledge of the complete install
public class VisualStudioInstall {
    private static final String NATIVEPREFIX_AMD64 = "win32-amd64";

    private static final String PLATFORM_AMD64_AMD64 = "amd64";
    private static final String PLATFORM_AMD64_ARM = "amd64_arm";
    private static final String PLATFORM_AMD64_X86 = "amd64_x86";
    private static final String PLATFORM_X86_AMD64 = "x86_amd64";
    private static final String PLATFORM_X86_ARM = "x86_arm";
    private static final String PLATFORM_X86_IA64 = "x86_ia64";
    private static final String PLATFORM_X86_X86 = "x86";

    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String LINKER_FILENAME = "link.exe";
    private static final String ARCHIVER_FILENAME = "lib.exe";
    private static final String ASSEMBLER_FILENAME_AMD64 = "ml64.exe";
    private static final String ASSEMBLER_FILENAME_ARM = "armasm.exe";
    private static final String ASSEMBLER_FILENAME_IA64 = "ias.exe";
    private static final String ASSEMBLER_FILENAME_X86 = "ml.exe";

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

    private final Map<String, String> availableBinPaths;
    private final String visualStudioVersion;
    private final File visualStudioDir;
    private final File visualCppDir;

    public VisualStudioInstall(File visualStudioDir, String visualStudioVersion) {
        this.visualStudioDir = visualStudioDir;
        this.visualStudioVersion = (visualStudioVersion != null) ? visualStudioVersion : VisualStudioVersion.VS_2010;
        visualCppDir = new File(visualStudioDir, "VC");
        availableBinPaths = getAvailableBinPaths(visualCppDir);
    }

    public String getVisualStudioVersion() {
        return visualStudioVersion;
    }

    public boolean isSupportedPlatform(Platform targetPlatform) {
        return targetPlatform.getOperatingSystem().isWindows()
                && isSupportedArchitecture((ArchitectureInternal) targetPlatform.getArchitecture());
    }

    private boolean isSupportedArchitecture(ArchitectureInternal targetArch) {
        if (targetArch == ArchitectureInternal.TOOL_CHAIN_DEFAULT) {
            return true;
        }

        boolean isNativeAmd64 = NATIVEPREFIX_AMD64.equals(org.gradle.internal.os.OperatingSystem.current().getNativePrefix());

        if (targetArch.isAmd64()) {
            return (isNativeAmd64 && availableBinPaths.containsKey(PLATFORM_AMD64_AMD64)) || availableBinPaths.containsKey(PLATFORM_X86_AMD64);
        }

        if (targetArch.isArm()) {
            return (isNativeAmd64 && availableBinPaths.containsKey(PLATFORM_AMD64_ARM)) || availableBinPaths.containsKey(PLATFORM_X86_ARM);
        }

        if (targetArch.isIa64()) {
            return availableBinPaths.containsKey(PLATFORM_X86_IA64);
        }

        return (isNativeAmd64 && availableBinPaths.containsKey(PLATFORM_AMD64_X86)) || availableBinPaths.containsKey(PLATFORM_X86_X86);
    }

    public File getVisualStudioDir() {
        return visualStudioDir;
    }

    public File getCompiler(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), COMPILER_FILENAME);
    }

    public File getLinker(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), LINKER_FILENAME);
    }

    public File getAssembler(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), getAssemblerExe(targetPlatform));
    }

    public File getStaticLibArchiver(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), ARCHIVER_FILENAME);
    }

    public File getVisualCppBin(Platform targetPlatform) {
        File file = getVisualCppBinForArchitecture(architecture(targetPlatform));
        if (file == null) {
            return new File(visualCppDir, BINPATH_X86_X86);
        }
        return file;
    }

    private File getVisualCppBinForArchitecture(ArchitectureInternal targetArch) {
        boolean isNativeAMD64 = NATIVEPREFIX_AMD64.equals(org.gradle.internal.os.OperatingSystem.current().getNativePrefix());

        if (targetArch.isAmd64()) {
            return getCompatibleVisualCppBin(isNativeAMD64, PLATFORM_AMD64_AMD64, PLATFORM_X86_AMD64);
        }

        if (targetArch.isI386()) {
            return getCompatibleVisualCppBin(isNativeAMD64, PLATFORM_AMD64_X86, PLATFORM_X86_X86);
        }

        if (targetArch.isIa64()) {
            return new File(visualCppDir, BINPATH_X86_IA64);
        }

        if (targetArch.isArm()) {
            return getCompatibleVisualCppBin(isNativeAMD64, PLATFORM_AMD64_ARM, PLATFORM_X86_ARM);
        }

        return null;
    }

    private File getCompatibleVisualCppBin(boolean isNativeAmd64, String platformAmd64, String platformFallback) {
        String path = null;

        if (isNativeAmd64) {
            path = availableBinPaths.get(platformAmd64);
        }

        if (path == null) {
            path = availableBinPaths.get(platformFallback);
        }

        if (path == null) {
            return null;
        }

        return new File(visualCppDir, path);
    }

    public File getCommonIdeBin() {
        return new File(visualStudioDir, "Common7/IDE");
    }

    public File getVisualCppInclude() {
        return new File(visualCppDir, "include");
    }

    public File getVisualCppLib(Platform platform) {
        if (architecture(platform).isAmd64()) {
            return new File(visualCppDir, LIBPATH_AMD64);
        }
        if (architecture(platform).isArm()) {
            return new File(visualCppDir, LIBPATH_ARM);
        }
        if (architecture(platform).isIa64()) {
            return new File(visualCppDir, LIBPATH_IA64);
        }
        return new File(visualCppDir, LIBPATH_X86);
    }

    private String getAssemblerExe(Platform platform) {
        if (architecture(platform).isAmd64()) {
            return ASSEMBLER_FILENAME_AMD64;
        }
        if (architecture(platform).isArm()) {
            return ASSEMBLER_FILENAME_ARM;
        }
        if (architecture(platform).isIa64()) {
            return ASSEMBLER_FILENAME_IA64;
        }
        return ASSEMBLER_FILENAME_X86;
    }

    private ArchitectureInternal architecture(Platform platform) {
        return (ArchitectureInternal) platform.getArchitecture();
    }

    private static Map<String, String> getAvailableBinPaths(File visualCppDir) {
        Map<String, String> availableBinPaths = new HashMap<String, String>();

        if (NATIVEPREFIX_AMD64.equals(org.gradle.internal.os.OperatingSystem.current().getNativePrefix())) {
            addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_AMD64_AMD64, PLATFORM_AMD64_AMD64);
            addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_AMD64_ARM, PLATFORM_AMD64_ARM);
            addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_AMD64_X86, PLATFORM_AMD64_X86);
        }

        addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_X86_AMD64, PLATFORM_X86_AMD64);
        addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_X86_ARM, PLATFORM_X86_ARM);
        addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_X86_IA64, PLATFORM_X86_IA64);
        addBinPathIfAvailable(availableBinPaths, visualCppDir, BINPATH_X86_X86, PLATFORM_X86_X86);

        return availableBinPaths;
    }

    private static void addBinPathIfAvailable(Map<String, String> availableBinPaths, File visualCppDir, String path, String platform) {
        File file = new File(visualCppDir, path);
        if (file.isDirectory()) {
            availableBinPaths.put(platform, path);
        }
    }
}
