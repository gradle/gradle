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

import org.gradle.api.Named;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.util.VersionNumber;

import java.io.File;

public class WindowsSdk implements Named {
    private static final String[] BINPATHS_X86 = {
        "bin/x86",
        "Bin"
    };
    private static final String[] BINPATHS_AMD64 = {
        "bin/x64"
    };
    private static final String[] BINPATHS_IA64 = {
        "bin/IA64"
    };
    private static final String[] BINPATHS_ARM = {
        "bin/arm"
    };
    private static final String LIBPATH_SDK8 = "Lib/win8/";
    private static final String LIBPATH_SDK81 = "Lib/winv6.3/um/";
    private static final String[] LIBPATHS_X86 = {
        LIBPATH_SDK81 + "x86",
        LIBPATH_SDK8 + "x86",
        "lib"
    };
    private static final String[] LIBPATHS_AMD64 = {
        LIBPATH_SDK81 + "x64",
        LIBPATH_SDK8 + "x64",
        "lib/x64"
    };
    private static final String[] LIBPATHS_IA64 = {
        "lib/IA64"
    };
    private static final String[] LIBPATHS_ARM = {
        LIBPATH_SDK81 + "arm",
        LIBPATH_SDK8 + "arm"
    };

    private final File baseDir;
    private final VersionNumber version;
    private final String name;

    public WindowsSdk(File baseDir, VersionNumber version, String name) {
        this.baseDir = baseDir;
        this.version = version;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public File getResourceCompiler(NativePlatformInternal platform) {
        return new File(getBinDir(platform), "rc.exe");
    }

    public File getBinDir(NativePlatformInternal platform) {
        if (architecture(platform).isAmd64()) {
            return getAvailableFile(BINPATHS_AMD64);
        }
        if (architecture(platform).isIa64()) {
            return getAvailableFile(BINPATHS_IA64);
        }
        if (architecture(platform).isArm()) {
            return getAvailableFile(BINPATHS_ARM);
        }
        return getAvailableFile(BINPATHS_X86);
    }

    public File[] getIncludeDirs() {
        File[] includesSdk8 = new File[] {
            new File(baseDir, "Include/shared"),
            new File(baseDir, "Include/um")
        };
        for (File file : includesSdk8) {
            if (!file.isDirectory()) {
                return new File[] {
                    new File(baseDir, "Include")
                };
            }
        }
        return includesSdk8;
    }

    public File getLibDir(NativePlatformInternal platform) {
        if (architecture(platform).isAmd64()) {
            return getAvailableFile(LIBPATHS_AMD64);
        }
        if (architecture(platform).isIa64()) {
            return getAvailableFile(LIBPATHS_IA64);
        }
        if (architecture(platform).isArm()) {
            return getAvailableFile(LIBPATHS_ARM);
        }
        return getAvailableFile(LIBPATHS_X86);
    }

    private ArchitectureInternal architecture(NativePlatformInternal platform) {
        return platform.getArchitecture();
    }

    private File getAvailableFile(String... candidates) {
        for (String candidate : candidates) {
            File file = new File(baseDir, candidate);
            if (file.isDirectory()) {
                return file;
            }
        }

        return new File(baseDir, candidates[0]);
    }

}
