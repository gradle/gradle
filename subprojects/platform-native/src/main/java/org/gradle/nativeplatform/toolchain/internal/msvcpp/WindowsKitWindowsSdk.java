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

import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WindowsKitWindowsSdk extends WindowsKitComponent implements WindowsSdk {
    private final File binDir;

    public WindowsKitWindowsSdk(File baseDir, VersionNumber version, File binDir, String name) {
        super(baseDir, version, name);
        this.binDir = binDir;
    }

    @Override
    public PlatformWindowsSdk forPlatform(final NativePlatformInternal platform) {
        return new WindowsKitBackedSdk(platform);
    }

    private class WindowsKitBackedSdk implements PlatformWindowsSdk {
        private final NativePlatformInternal platform;

        public WindowsKitBackedSdk(NativePlatformInternal platform) {
            this.platform = platform;
        }

        @Override
        public VersionNumber getVersion() {
            return WindowsKitWindowsSdk.this.getVersion();
        }

        @Override
        public List<File> getIncludeDirs() {
            return Arrays.asList(
                new File(getBaseDir(), "Include/" + getVersion().toString() + "/um"),
                new File(getBaseDir(), "Include/" + getVersion().toString() + "/shared")
            );
        }

        @Override
        public List<File> getLibDirs() {
            String platformDir = "x86";
            if (platform.getArchitecture().isAmd64()) {
                platformDir = "x64";
            }
            if (platform.getArchitecture().isArm()) {
                platformDir = "arm";
            }
            return Collections.singletonList(new File(getBaseDir(), "Lib/" + getVersion().toString() + "/um/" + platformDir));
        }

        @Override
        public File getResourceCompiler() {
            return new File(getBinDir(), "rc.exe");
        }

        @Override
        public Map<String, String> getPreprocessorMacros() {
            return Collections.emptyMap();
        }

        @Override
        public List<File> getPath() {
            return Collections.singletonList(getBinDir());
        }

        private File getBinDir() {
            if (platform.getArchitecture().isAmd64()) {
                return new File(binDir, "x64");
            }
            if (platform.getArchitecture().isArm()) {
                return new File(binDir, "arm");
            }
            return new File(binDir, "x86");
        }
    }
}
