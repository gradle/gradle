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

public class VisualStudioInstall {
    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String LINKER_FILENAME = "link.exe";
    private static final String ARCHIVER_FILENAME = "lib.exe";
    private static final String BINPATH_AMD64_AMD64 = "bin/amd64";
    private static final String BINPATH_AMD64_ARM = "bin/amd64_arm";
    private static final String BINPATH_AMD64_X86 = "bin/amd64_x86";
    private static final String BINPATH_IA64_IA64 = "bin/ia64";
    private static final String BINPATH_X86_AMD64 = "bin/x86_amd64";
    private static final String BINPATH_X86_ARM = "bin/x86_arm";
    private static final String BINPATH_X86_IA64 = "bin/x86_ia64";
    private static final String BINPATH_X86_X86 = "bin";
    private static final String BINPATH_DEFAULT = BINPATH_X86_X86;

    private final String visualStudioVersion;
    private final File visualStudioDir;
    private final File visualCppDir;

    public VisualStudioInstall(File visualStudioDir, String visualStudioVersion) {
        this.visualStudioDir = visualStudioDir;
        this.visualStudioVersion = visualStudioVersion;
        visualCppDir = new File(visualStudioDir, "VC");
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

        if (visualStudioVersion.equals(VisualStudioVersion.VS_2013)) {
            return targetArch.isI386() || targetArch.isAmd64() || targetArch.isArm();
        }

        if (visualStudioVersion.equals(VisualStudioVersion.VS_2012)) {
            return targetArch.isI386() || targetArch.isIa64();
        }

        if (visualStudioVersion.equals(VisualStudioVersion.VS_2010)) {
            return targetArch.isI386() || targetArch.isAmd64() || targetArch.isIa64();
        }

        return false;
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

    public File getVisualCppBin() {
        return new File(visualCppDir, "bin");
    }

    public File getVisualCppBin(Platform platform) {
        String osPrefix = org.gradle.internal.os.OperatingSystem.current().getNativePrefix();
        boolean hostIsAmd64 = "win32-amd64".equals(osPrefix);
        boolean hostIsIA64 = "win32-ia64".equals(osPrefix);

        if (hostIsAmd64) {
            if (architecture(platform).isAmd64()) {
                return new File(visualCppDir, BINPATH_AMD64_AMD64);
            }

            if (architecture(platform).isArm()) {
                return new File(visualCppDir, BINPATH_AMD64_ARM);
            }

            if (architecture(platform).isI386()) {
                return new File(visualCppDir, BINPATH_AMD64_X86);
            }
        } else if (hostIsIA64) {
            if (architecture(platform).isIa64()) {
                return new File(visualCppDir, BINPATH_IA64_IA64);
            }
        } else {
            if (architecture(platform).isAmd64()) {
                return new File(visualCppDir, BINPATH_X86_AMD64);
            }

            if (architecture(platform).isIa64()) {
                return new File(visualCppDir, BINPATH_X86_IA64);
            }

            if (architecture(platform).isArm()) {
                return new File(visualCppDir, BINPATH_X86_ARM);
            }

            if (architecture(platform).isI386()) {
                return new File(visualCppDir, BINPATH_X86_X86);
            }
        }

        return new File(visualCppDir, BINPATH_DEFAULT);
    }

    public File getCommonIdeBin() {
        return new File(visualStudioDir, "Common7/IDE");
    }

    public File getVisualCppInclude() {
        return new File(visualCppDir, "include");
    }

    public File getVisualCppLib(Platform platform) {
        if (architecture(platform).isAmd64()) {
            return new File(visualCppDir, "lib/amd64");
        }
        if (architecture(platform).isIa64()) {
            return new File(visualCppDir, "lib/ia64");
        }
        return new File(visualCppDir, "lib");
    }

    private String getAssemblerExe(Platform platform) {
        if (architecture(platform).isAmd64()) {
            return "ml64.exe";
        }
        if (architecture(platform).isIa64()) {
            return "ias.exe";
        }
        return "ml.exe";
    }

    private ArchitectureInternal architecture(Platform platform) {
        return (ArchitectureInternal) platform.getArchitecture();
    }
}
