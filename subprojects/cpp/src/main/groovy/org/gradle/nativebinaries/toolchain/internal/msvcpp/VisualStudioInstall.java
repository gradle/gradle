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

import java.io.File;

public class VisualStudioInstall {
    private final File visualStudioDir;
    private final File visualCppDir;

    public VisualStudioInstall(File visualStudioDir) {
        this.visualStudioDir = visualStudioDir;
        visualCppDir = new File(visualStudioDir, "VC");
    }

    public File getVisualStudioDir() {
        return visualStudioDir;
    }

    public File getCompiler(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), "cl.exe");
    }

    public File getLinker(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), "link.exe");
    }

    public File getAssembler(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), getAssemblerExe(targetPlatform));
    }

    public File getStaticLibArchiver(Platform targetPlatform) {
        return new File(getVisualCppBin(targetPlatform), "lib.exe");
    }

    public File getVisualCppBin(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return new File(visualCppDir, "bin/x86_amd64");
            default:
                return new File(visualCppDir, "bin");
        }
    }

    public File getCommonIdeBin() {
        return new File(visualStudioDir, "Common7/IDE");
    }

    public File getVisualCppInclude() {
        return new File(visualCppDir, "include");
    }

    public File getVisualCppLib(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return new File(visualCppDir, "lib/amd64");
            default:
                return new File(visualCppDir, "lib");
        }
    }

    private String getAssemblerExe(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return "ml64.exe";
            default:
                return "ml.exe";
        }
    }
}
