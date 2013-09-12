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
    private final File windowsSdkDir;

    public VisualStudioInstall(File visualStudioDir, File windowsSdkDir) {
        this.visualStudioDir = visualStudioDir;
        visualCppDir = new File(visualStudioDir, "VC");

        // TODO:DAZ Should the windows SDK be part of the tool chain? How should we deal with system libraries?
        this.windowsSdkDir = windowsSdkDir;
    }

    public File getVisualStudioDir() {
        return visualStudioDir;
    }

    public File getWindowsSdkDir() {
        return windowsSdkDir;
    }

    public void configureTools(ToolRegistry tools, Platform platform) {
        tools.setPath(Arrays.asList(
                new File(visualStudioDir, "Common7/IDE"),
                new File(visualStudioDir, "Common7/Tools"),
                getVisualCppTools(platform),
                new File(visualCppDir, "VCPackages"),
                new File(windowsSdkDir, "Bin")
        ));
        tools.getEnvironment().put("INCLUDE", new File(visualCppDir, "include").getAbsolutePath());
        tools.getEnvironment().put("LIB", getSystemStubs(platform));

        configureAssembler(tools, platform);
    }

    private File getVisualCppTools(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return new File(visualCppDir, "bin/x86_amd64");
            default:
                return new File(visualCppDir, "bin");
        }
    }

    private String getSystemStubs(Platform platform) {
        switch (platform.getArchitecture()) {
            case AMD64:
                return new File(visualCppDir, "lib/amd64").getAbsolutePath()
                        + File.pathSeparator
                        + new File(windowsSdkDir, "lib/x64").getAbsolutePath();
            default:
                return new File(visualCppDir, "lib").getAbsolutePath()
                        + File.pathSeparator
                        + new File(windowsSdkDir, "lib").getAbsolutePath();
        }
    }

    private void configureAssembler(ToolRegistry tools, Platform platform) {
        // TODO:DAZ This isn't great: should really track user-configured exe names separate from implicit names
        if (platform.getArchitecture() == Platform.Architecture.AMD64) {
            // If the named assembler is found in configured path, just use it. This permits user to point to alternative assembler.
            if (tools.locate(ToolType.ASSEMBLER) == null) {
                // Otherwise, use standard visual studio 64-bit assembler
                tools.setExeName(ToolType.ASSEMBLER, "ml64.exe");
            }
        }
    }
}
