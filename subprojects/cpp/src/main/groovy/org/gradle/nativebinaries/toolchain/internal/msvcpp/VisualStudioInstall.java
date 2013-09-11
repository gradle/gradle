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
import java.util.*;

public class VisualStudioInstall {
    private final File installDir;
    private final File visualStudioDir;
    private final File windowsSdkDir;
    private final List<File> pathEntries = new ArrayList<File>();
    private final Map<String, String> environment = new HashMap<String, String>();

    public VisualStudioInstall(File installDir) {
        this.installDir = installDir;
        this.visualStudioDir = locateVisualStudio(installDir);
        File vcDir = new File(visualStudioDir, "VC");

        // TODO:DAZ Should the windows SDK be part of the tool chain? How should we deal with system libraries?
        windowsSdkDir = locateWindowsSdk(visualStudioDir);

        addPathEntries(
                new File(visualStudioDir, "Common7/IDE"),
                new File(vcDir, "bin"),
                new File(visualStudioDir, "Common7/Tools"),
                new File(vcDir, "VCPackages"),
                new File(windowsSdkDir, "Bin")
        );

        environment.put("INCLUDE", new File(vcDir, "include").getAbsolutePath());
        environment.put("LIB", new File(vcDir, "lib").getAbsolutePath() + File.pathSeparator + new File(windowsSdkDir, "lib").getAbsolutePath());
    }

    private File locateVisualStudio(File installDir) {
        // Handle the visual studio install, VC, or VC/bin directories.
        if (new File(installDir, "cl.exe").isFile()) {
            return installDir.getParentFile().getParentFile();
        } else if (new File(installDir, "bin/cl.exe").isFile()) {
            return installDir.getParentFile();
        }
        return installDir;
    }

    private File locateWindowsSdk(File visualStudioDir) {
        File programFiles = visualStudioDir.getParentFile();
        File winsdk71 = new File(programFiles, "Microsoft SDKs/Windows/v7.1");
        if (winsdk71.isDirectory()) {
            return winsdk71;
        }
        return new File(programFiles, "Microsoft SDKs/Windows/v7.0A");
    }

    private void addPathEntries(File... entry) {
        pathEntries.addAll(Arrays.asList(entry));
    }

    public File getInstallDir() {
        return installDir;
    }

    public List<File> getPathEntries() {
        return pathEntries;
    }

    public Map<String, String> getEnvironment() {
        return environment;
    }

    public boolean isInstalled() {
        return new File(visualStudioDir, "VC/bin/cl.exe").isFile();
    }

    public File getVisualStudioDir() {
        return visualStudioDir;
    }

    public File getWindowsSdkDir() {
        return windowsSdkDir;
    }
}
