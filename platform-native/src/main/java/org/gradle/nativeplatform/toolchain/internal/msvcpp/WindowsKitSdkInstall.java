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
import org.gradle.util.internal.VersionNumber;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WindowsKitSdkInstall extends WindowsKitInstall implements WindowsSdkInstall {
    private final File binDir;

    public WindowsKitSdkInstall(File baseDir, VersionNumber version, File binDir, String name) {
        super(baseDir, version, name);
        this.binDir = binDir;
    }

    @Override
    public WindowsSdk forPlatform(final NativePlatformInternal platform) {
        if (platform.getArchitecture().isAmd64()) {
            return new WindowsKitBackedSdk("x64");
        }
        if (platform.getArchitecture().isArm()) {
            return new WindowsKitBackedSdk("arm");
        }
        if (platform.getArchitecture().isI386()) {
            return new WindowsKitBackedSdk("x86");
        }
        throw new UnsupportedOperationException(String.format("Unsupported %s for %s.", platform.getArchitecture().getDisplayName(), toString()));
    }

    private class WindowsKitBackedSdk implements WindowsSdk {
        private final String platformDirName;

        WindowsKitBackedSdk(String platformDirName) {
            this.platformDirName = platformDirName;
        }

        @Override
        public VersionNumber getImplementationVersion() {
            return WindowsKitSdkInstall.this.getVersion();
        }

        @Override
        public VersionNumber getSdkVersion() {
            return getImplementationVersion();
        }

        @Override
        public List<File> getIncludeDirs() {
            return Arrays.asList(
                new File(getBaseDir(), "Include/" + getImplementationVersion().toString() + "/um"),
                new File(getBaseDir(), "Include/" + getImplementationVersion().toString() + "/shared")
            );
        }

        @Override
        public List<File> getLibDirs() {
            return Collections.singletonList(new File(getBaseDir(), "Lib/" + getImplementationVersion().toString() + "/um/" + platformDirName));
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
            return new File(binDir, platformDirName);
        }
    }
}
