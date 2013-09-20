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

import org.gradle.api.specs.Spec;
import org.gradle.internal.os.OperatingSystem;

import java.io.File;

public class VisualStudioLocator {
    private final OperatingSystem os;

    public VisualStudioLocator() {
        this(OperatingSystem.current());
    }

    public VisualStudioLocator(OperatingSystem os) {
        this.os = os;
    }

    public File locateVisualStudio(File candidate) {
        return locateInHierarchy(candidate, isVisualStudio());
    }

    public File locateDefaultVisualStudio() {
        Spec<File> isVisualStudio = isVisualStudio();
        // If cl.exe is on the path, assume it is contained within a visual studio install
        File compilerInPath = os.findInPath("cl.exe");
        if (compilerInPath != null) {
            return locateInHierarchy(compilerInPath, isVisualStudio);
        }

        // TODO:DAZ Use %PROGRAMFILES% environment variable
        return locateInCandidates(isVisualStudio,
                "C:/Program Files (x86)/Microsoft Visual Studio 10.0",
                "C:/Program Files/Microsoft Visual Studio 10.0");
    }

    private Spec<File> isVisualStudio() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return new File(element, "VC/bin/cl.exe").isFile();
            }
        };
    }

    public File locateDefaultWindowsSdk() {
        // If rc.exe is on the path, assume it is contained within a Windows SDK
        File resourceCompilerInPath = os.findInPath("rc.exe");
        if (resourceCompilerInPath != null) {
            return locateInHierarchy(resourceCompilerInPath, isWindowsSdk());
        }

        // TODO:DAZ Use %PROGRAMFILES% environment variable
        return locateInCandidates(isWindowsSdk(),
                "C:/Program Files (x86)/Microsoft SDKs/Windows/v7.1",
                "C:/Program Files/Microsoft SDKs/Windows/v7.1",
                "C:/Program Files (x86)/Microsoft SDKs/Windows/v7.0A",
                "C:/Program Files/Microsoft SDKs/Windows/v7.0A");
    }

    private Spec<File> isWindowsSdk() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return isWindowsSdk(element);
            }
        };
    }

    private boolean isWindowsSdk(File candidate) {
        return new File(candidate, "bin/rc.exe").isFile() && new File(candidate, "lib/kernel32.lib").isFile();
    }

    private File locateInCandidates(Spec<File> condition, String... candidateLocations) {
        for (String candidateLocation : candidateLocations) {
            File candidate = new File(candidateLocation);
            if (condition.isSatisfiedBy(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private File locateInHierarchy(File candidate, Spec<File> condition) {
        while (candidate != null) {
            if (condition.isSatisfiedBy(candidate)) {
                return candidate;
            }
            candidate = candidate.getParentFile();
        }
        return null;
    }
}
