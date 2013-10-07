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

import org.gradle.api.Named;
import org.gradle.nativebinaries.Platform;
import org.gradle.nativebinaries.internal.ArchitectureInternal;

import java.io.File;

public class WindowsSdk implements Named {
    private final File baseDir;

    public WindowsSdk(File baseDir) {
        this.baseDir = baseDir;
    }

    public String getName() {
        return "Windows SDK " + getVersion();
    }

    public String getVersion() {
        return baseDir.getName();
    }

    public File getBinDir() {
        return new File(baseDir, "Bin");
    }

    public File getIncludeDir() {
        return new File(baseDir, "Include");
    }

    public File getLibDir(Platform platform) {
        if (architecture(platform).isAmd64()) {
            return new File(baseDir, "lib/x64");
        }
        if (architecture(platform).isIa64()) {
            return new File(baseDir, "lib/IA64");
        }
        return new File(baseDir, "lib");
    }

    private ArchitectureInternal architecture(Platform platform) {
        return (ArchitectureInternal) platform.getArchitecture();
    }

}
