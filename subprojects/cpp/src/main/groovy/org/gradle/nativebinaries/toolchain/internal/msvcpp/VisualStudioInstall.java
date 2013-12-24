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

import java.io.File;
import java.util.List;
import java.util.Map;

import org.gradle.api.Named;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.util.VersionNumber;

public class VisualStudioInstall implements Named {
    private final VisualCppInstall visualCppInstall;
    private final VersionNumber version;
    private final File baseDir;
    private final String name;

    public VisualStudioInstall(File baseDir, VersionNumber version, String name, VisualCppInstall visualCppInstall) {
        this.baseDir = baseDir;
        this.version = version;
        this.name = name;
        this.visualCppInstall = visualCppInstall;
    }

    public String getName() {
        return name;
    }

    public VersionNumber getVersion() {
        return version;
    }

    public boolean isSupportedPlatform(Platform targetPlatform) {
        return visualCppInstall.isSupportedPlatform(targetPlatform);
    }

    public File getVisualStudioDir() {
        return baseDir;
    }

    public File getCompiler(Platform targetPlatform) {
        return visualCppInstall.getCompiler(targetPlatform);
    }

    public File getLinker(Platform targetPlatform) {
        return visualCppInstall.getLinker(targetPlatform);
    }

    public File getAssembler(Platform targetPlatform) {
        return visualCppInstall.getAssembler(targetPlatform);
    }

    public File getStaticLibArchiver(Platform targetPlatform) {
        return visualCppInstall.getArchiver(targetPlatform);
    }

    public List<File> getVisualCppPathForPlatform(Platform targetPlatform) {
        return visualCppInstall.getPath(targetPlatform);
    }

    public File getVisualCppBin(Platform targetPlatform) {
        return visualCppInstall.getBinaryPath(targetPlatform);
    }

    public Map<String, String> getVisualCppDefines(Platform targetPlatform) {
        return visualCppInstall.getDefinitions(targetPlatform);
    }

    public File getVisualCppInclude(Platform targetPlatform) {
        return visualCppInstall.getIncludePath(targetPlatform);
    }

    public File getVisualCppLib(Platform targetPlatform) {
        return visualCppInstall.getLibraryPath(targetPlatform);
    }
 
}
