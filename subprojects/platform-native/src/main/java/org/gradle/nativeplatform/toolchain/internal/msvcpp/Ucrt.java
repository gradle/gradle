/*
 * Copyright 2015 the original author or authors.
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

public class Ucrt implements Named {
    private final File baseDir;
    private final String name;
    private final VersionNumber version;

    public Ucrt(File baseDir, String name, VersionNumber version) {
        this.baseDir = baseDir;
        this.name = name;
        this.version = version;
    }

    public File getBaseDir() {
        return baseDir;
    }

    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public File[] getIncludeDirs() {
        return new File[] {
            new File(new File(new File(baseDir, "Include"), version.toString()), "ucrt")
        };
    }

    public File getLibDir(NativePlatformInternal platform) {
        String platformDir = "x86";
        if (architecture(platform).isAmd64()) {
            platformDir = "x64";
        }
        if (architecture(platform).isArm()) {
            platformDir = "arm";
        }
        return new File(new File(new File(new File(baseDir, "Lib"), version.toString()), "ucrt"), platformDir);
    }

    private ArchitectureInternal architecture(NativePlatformInternal platform) {
        return platform.getArchitecture();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Ucrt ucrt = (Ucrt) o;

        if (!baseDir.equals(ucrt.baseDir)) {
            return false;
        }
        if (!name.equals(ucrt.name)) {
            return false;
        }
        return version.equals(ucrt.version);

    }

    @Override
    public int hashCode() {
        int result = baseDir.hashCode();
        result = 31 * result + name.hashCode();
        result = 31 * result + version.hashCode();
        return result;
    }
}
