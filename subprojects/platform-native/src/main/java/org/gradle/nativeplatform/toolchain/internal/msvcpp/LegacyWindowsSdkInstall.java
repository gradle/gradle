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

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LegacyWindowsSdkInstall implements WindowsSdkInstall {
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
    private static final String LIBPATH_SDK8 = "Lib/win8/um/";
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

    public LegacyWindowsSdkInstall(File baseDir, VersionNumber version, String name) {
        this.baseDir = baseDir;
        this.version = version;
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public VersionNumber getVersion() {
        return version;
    }

    public File getBaseDir() {
        return baseDir;
    }

    @Override
    public WindowsSdk forPlatform(final NativePlatformInternal platform) {
        if (platform.getArchitecture().isAmd64()) {
            return new LegacyPlatformWindowsSdk(BINPATHS_AMD64, LIBPATHS_AMD64);
        }
        if (platform.getArchitecture().isIa64()) {
            return new LegacyPlatformWindowsSdk(BINPATHS_IA64, LIBPATHS_IA64);
        }
        if (platform.getArchitecture().isArm()) {
            return new LegacyPlatformWindowsSdk(BINPATHS_ARM, LIBPATHS_ARM);
        }
        if (platform.getArchitecture().isI386()) {
            return new LegacyPlatformWindowsSdk(BINPATHS_X86, LIBPATHS_X86);
        }
        throw new UnsupportedOperationException(String.format("Unsupported %s for %s.", platform.getArchitecture().getDisplayName(), toString()));
    }

    private class LegacyPlatformWindowsSdk implements WindowsSdk {
        private final String[] binPaths;
        private final String[] libPaths;

        LegacyPlatformWindowsSdk(String[] binPaths, String[] libPaths) {
            this.binPaths = binPaths;
            this.libPaths = libPaths;
        }

        @Override
        public VersionNumber getImplementationVersion() {
            return version;
        }

        @Override
        public VersionNumber getSdkVersion() {
            return version;
        }

        @Override
        public List<File> getIncludeDirs() {
            List<File> includesSdk8 = Arrays.asList(
                new File(baseDir, "Include/shared"),
                new File(baseDir, "Include/um")
            );
            for (File file : includesSdk8) {
                if (!file.isDirectory()) {
                    return Collections.singletonList(new File(baseDir, "Include"));
                }
            }
            return includesSdk8;
        }

        @Override
        public List<File> getLibDirs() {
            return Collections.singletonList(getAvailableFile(libPaths));
        }

        @Override
        public Map<String, String> getPreprocessorMacros() {
            return Collections.emptyMap();
        }

        @Override
        public File getResourceCompiler() {
            return new File(getBinDir(), "rc.exe");
        }

        @Override
        public List<File> getPath() {
            return Collections.singletonList(getBinDir());
        }

        private File getBinDir() {
            return getAvailableFile(binPaths);
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
}
