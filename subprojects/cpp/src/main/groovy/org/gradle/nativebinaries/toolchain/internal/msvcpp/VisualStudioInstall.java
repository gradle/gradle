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
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.ToolType;

import java.io.File;
import java.util.*;

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

    public void configureTools(ToolRegistry tools, Platform platform) {
        tools.setPath(Arrays.asList(
                new File(visualStudioDir, "Common7/IDE"),
                new File(visualStudioDir, "Common7/Tools"),
                getVisualCppBin(platform),
                new File(visualCppDir, "VCPackages")
        ));
        tools.getEnvironment().put("INCLUDE", getVisualCppInclude().getAbsolutePath());
        tools.getEnvironment().put("LIB", getVisualCppLib(platform).getAbsolutePath());

        tools.setExeName(ToolType.ASSEMBLER, getAssemblerExe(platform));
    }

    private File getVisualCppBin(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return new File(visualCppDir, "bin/x86_amd64");
            default:
                return new File(visualCppDir, "bin");
        }
    }

    private File getVisualCppInclude() {
        return new File(visualCppDir, "include");
    }

    private File getVisualCppLib(Platform platform) {
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
