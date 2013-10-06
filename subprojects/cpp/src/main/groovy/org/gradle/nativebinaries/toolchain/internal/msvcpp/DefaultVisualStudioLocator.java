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
import java.util.ArrayList;
import java.util.List;

public class DefaultVisualStudioLocator implements VisualStudioLocator {
    private final OperatingSystem os;

    public DefaultVisualStudioLocator() {
        this(OperatingSystem.current());
    }

    public DefaultVisualStudioLocator(OperatingSystem os) {
        this.os = os;
    }

    public Search locateVisualStudio(File candidate) {
        return locateInHierarchy(candidate, isVisualStudio());
    }

    public Search locateDefaultVisualStudio() {
        Spec<File> isVisualStudio = isVisualStudio();
        // If cl.exe is on the path, assume it is contained within a visual studio install
        File compilerInPath = os.findInPath("cl.exe");
        if (compilerInPath != null) {
            return locateInHierarchy(compilerInPath, isVisualStudio);
        }

        return locateInProgramFiles(isVisualStudio, "/Microsoft Visual Studio 10.0");
    }

    private Spec<File> isVisualStudio() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return new File(element, "VC/bin/cl.exe").isFile();
            }
        };
    }

    public Search locateDefaultWindowsSdk() {
        // If rc.exe is on the path, assume it is contained within a Windows SDK
        File resourceCompilerInPath = os.findInPath("rc.exe");
        if (resourceCompilerInPath != null) {
            return locateInHierarchy(resourceCompilerInPath, isWindowsSdk());
        }

        return locateInProgramFiles(isWindowsSdk(),
                "Microsoft SDKs/Windows/v7.1",
                "Microsoft SDKs/Windows/v7.0A");
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

    private Search locateInProgramFiles(Spec<File> condition, String... candidateLocations) {
        Search search = new Search();
        for (File candidate : programFileCandidates(candidateLocations)) {
            if (condition.isSatisfiedBy(candidate)) {
                search.found(candidate);
                break;
            }
            search.notFound(candidate);
        }
        return search;
    }

    private List<File> programFileCandidates(String... candidateDirectory) {
        String[] programFilesDirectories;

        // On 64-bit windows, PROGRAMFILES doesn't return a consistent location:
        // depends on whether the process requesting the environment variable is itself 32-bit or 64-bit.
        String programFilesX64 = System.getenv("ProgramW6432");
        String programFilesX86 = System.getenv("PROGRAMFILES(x86)");
        if (programFilesX64 != null && programFilesX86 != null) {
            programFilesDirectories = new String[]{programFilesX64, programFilesX86};
        } else {
            programFilesDirectories = new String[]{System.getenv("PROGRAMFILES")};
        }

        List<File> candidates = new ArrayList<File>(candidateDirectory.length * 2);
        for (String candidateLocation : candidateDirectory) {
            for (String programFilesDirectory : programFilesDirectories) {
                candidates.add(new File(programFilesDirectory, candidateLocation));
            }
        }
        return candidates;
    }

    private Search locateInHierarchy(File candidate, Spec<File> condition) {
        Search search = new Search();
        while (candidate != null) {
            if (condition.isSatisfiedBy(candidate)) {
                search.found(candidate);
                return search;
            }
            search.notFound(candidate);
            candidate = candidate.getParentFile();
        }
        return search;
    }

    public class Search implements SearchResult {
        private File file;
        private List<File> searchLocations = new ArrayList<File>();

        public boolean isFound() {
            return file != null;
        }

        public File getResult() {
            return file;
        }

        public List<File> getSearchLocations() {
            return searchLocations;
        }

        void notFound(File candidate) {
            searchLocations.add(candidate);
        }

        void found(File candidate) {
            this.file = candidate;
        }
    }
}
