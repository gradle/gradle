/*
 * Copyright 2017 the original author or authors.
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
import com.google.common.collect.Maps;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.List;
import java.util.Map;

public enum ArchitectureDescriptorBuilder {
    // See https://blogs.msdn.microsoft.com/vcblog/2016/10/07/compiler-tools-layout-in-visual-studio-15/

    // Host: x64
    // Target: x64
    LEGACY_AMD64_ON_AMD64("amd64", "bin/amd64", "lib/amd64", "ml64.exe"),
    AMD64_ON_AMD64("amd64", "bin/HostX64/x64", "lib/x64", "ml64.exe"),

    // Host: x64
    // Target: x86
    LEGACY_AMD64_ON_X86("amd64", "bin/x86_amd64", "lib/amd64", "ml64.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return LEGACY_X86_ON_X86.getBinPath(basePath);
        }
    },
    AMD64_ON_X86("amd64", "bin/HostX86/x64", "lib/x64", "ml64.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return X86_ON_X86.getBinPath(basePath);
        }
    },

    // Host: x86
    // Target: x64
    LEGACY_X86_ON_AMD64("x86", "bin/amd64_x86", "lib", "ml.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return LEGACY_AMD64_ON_AMD64.getBinPath(basePath);
        }
    },
    X86_ON_AMD64("x86", "bin/HostX64/x86", "lib/x86", "ml.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return AMD64_ON_AMD64.getBinPath(basePath);
        }
    },

    // Host: x86
    // Target: x86
    LEGACY_X86_ON_X86("x86", "bin", "lib", "ml.exe"),
    X86_ON_X86("x86", "bin/HostX86/x86", "lib/x86", "ml.exe"),

    // Host: x64
    // Target: arm
    LEGACY_ARM_ON_AMD64("arm", "bin/amd64_arm", "lib/arm", "armasm.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return LEGACY_AMD64_ON_AMD64.getBinPath(basePath);
        }

        @Override
        Map<String, String> getDefinitions() {
            Map<String, String> definitions = super.getDefinitions();
            definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
            return definitions;
        }
    },
    ARM_ON_AMD64("arm", "bin/Hostx64/arm", "lib/arm", "armasm.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return AMD64_ON_AMD64.getBinPath(basePath);
        }

        @Override
        Map<String, String> getDefinitions() {
            Map<String, String> definitions = super.getDefinitions();
            definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
            return definitions;
        }
    },

    ARM64_ON_X86("arm64", "bin/HostX86/arm64", "lib/arm64", "armasm64.exe"),
    ARM64_ON_AMD64("arm64", "bin/Hostx64/arm64", "lib/arm64", "armasm64.exe"),

    // Host: x86
    // Target: arm
    LEGACY_ARM_ON_X86("arm", "bin/x86_arm", "lib/arm", "armasm.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return LEGACY_X86_ON_X86.getBinPath(basePath);
        }

        @Override
        Map<String, String> getDefinitions() {
            Map<String, String> definitions = super.getDefinitions();
            definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
            return definitions;
        }
    },
    ARM_ON_X86("arm", "bin/HostX86/arm", "lib/arm", "armasm.exe") {
        @Override
        File getCrossCompilePath(File basePath) {
            return X86_ON_X86.getBinPath(basePath);
        }

        @Override
        Map<String, String> getDefinitions() {
            Map<String, String> definitions = super.getDefinitions();
            definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
            return definitions;
        }
    },

    // Host: x86
    // Target: ia64
    // (ia64 is no longer supported on later versions of Visual Studio)
    LEGACY_IA64_ON_X86("ia64", "bin/x86_ia64", "lib/ia64", "ias.exe")  {
        @Override
        File getCrossCompilePath(File basePath) {
            return LEGACY_X86_ON_X86.getBinPath(basePath);
        }
    };

    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_COMMONTOOLS = PATH_COMMON + "Tools/";
    private static final String PATH_COMMONIDE = PATH_COMMON + "IDE/";
    private static final String PATH_INCLUDE = "include/";
    private static final String DEFINE_ARMPARTITIONAVAILABLE = "_ARM_WINAPI_PARTITION_DESKTOP_SDK_AVAILABLE";
    private static final String COMPILER_FILENAME = "cl.exe";

    final Architecture architecture;
    final String binPath;
    final String libPath;
    final String asmFilename;

    ArchitectureDescriptorBuilder(String architecture, String binPath, String libPath, String asmFilename) {
        this.binPath = binPath;
        this.libPath = libPath;
        this.asmFilename = asmFilename;
        this.architecture = Architectures.forInput(architecture);
    }

    File getBinPath(File basePath) {
        return new File(basePath, binPath);
    }

    File getLibPath(File basePath) {
        return new File(basePath, libPath);
    }

    File getCompilerPath(File basePath) {
        return new File(getBinPath(basePath), COMPILER_FILENAME);
    }

    File getCrossCompilePath(File basePath) {
        return null;
    }

    Map<String, String> getDefinitions() {
        return Maps.newHashMap();
    }

    ArchitectureSpecificVisualCpp buildDescriptor(VersionNumber compilerVersion, File basePath, File vsPath) {
        File commonTools = new File(vsPath, PATH_COMMONTOOLS);
        File commonIde = new File(vsPath, PATH_COMMONIDE);
        List<File> paths = Lists.newArrayList(commonTools, commonIde);
        File crossCompilePath = getCrossCompilePath(basePath);
        if (crossCompilePath!=null) {
            paths.add(crossCompilePath);
        }
        File includePath = new File(basePath, PATH_INCLUDE);
        return new ArchitectureSpecificVisualCpp(compilerVersion, paths, getBinPath(basePath), getLibPath(basePath), getCompilerPath(basePath), includePath, asmFilename, getDefinitions());
    }
}
