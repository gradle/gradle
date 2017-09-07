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

public class WindowsKitWindowsSdk extends WindowsKitComponent implements WindowsSdk {

    public WindowsKitWindowsSdk(File baseDir, VersionNumber version, String name) {
        super(baseDir, version, name);
    }

    @Override
    public File getResourceCompiler(NativePlatformInternal platform) {
        return new File(getBinDir(platform), "rc.exe");
    }

    @Override
    public File getBinDir(NativePlatformInternal platform) {
        if (platform.getArchitecture().isAmd64()) {
            return new File(getBaseDir(), "bin/x64");
        }
        if (platform.getArchitecture().isArm()) {
            return new File(getBaseDir(), "bin/arm");
        }
        return new File(getBaseDir(), "bin/x86");
    }

    public File[] getIncludeDirs() {
        return new File[] {
            new File(getBaseDir(), "Include/" + getVersion().toString() + "/um"),
            new File(getBaseDir(), "Include/" + getVersion().toString() + "/shared")
        };
    }

    public File getLibDir(NativePlatformInternal platform) {
        String platformDir = "x86";
        if (platform.getArchitecture().isAmd64()) {
            platformDir = "x64";
        }
        if (platform.getArchitecture().isArm()) {
            platformDir = "arm";
        }
        return new File(getBaseDir(), "Lib/" + getVersion().toString() + "/um/" + platformDir);
    }
}
