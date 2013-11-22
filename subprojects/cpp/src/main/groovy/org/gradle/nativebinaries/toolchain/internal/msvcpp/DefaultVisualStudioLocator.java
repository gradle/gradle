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
    private static final String COMPILER_PATH = "VC/bin/";
    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String RESOURCE_PATH = "bin/";
    private static final String RESOURCE_PATH_WINSDK8 = "bin/x86/";
    private static final String RESOURCE_FILENAME = "rc.exe";
    private static final String KERNEL32_PATH = "lib/";
    private static final String KERNEL32_PATH_WINSDK8 = "lib/winv6.3/um/x86/";
    private static final String KERNEL32_FILENAME = "kernel32.lib";

    private static final VersionLookupPath[] VISUALSTUDIO_PATHS = {
        new VersionLookupPath("/Microsoft Visual Studio 12.0", VisualStudioVersion.VS_2013),
        new VersionLookupPath("/Microsoft Visual Studio 11.0", VisualStudioVersion.VS_2012),
        new VersionLookupPath("/Microsoft Visual Studio 10.0", VisualStudioVersion.VS_2010)
    };
    private static final String[] WINDOWSSDK_PATHS = {
        "Windows Kits/8.1",
        "Windows Kits/8.0",
        "Microsoft SDKs/Windows/v7.1A",
        "Microsoft SDKs/Windows/v7.1",
        "Microsoft SDKs/Windows/v7.0A"
    };

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
        File compilerInPath = os.findInPath(COMPILER_FILENAME);
        if (compilerInPath != null) {
            return locateInHierarchy(compilerInPath, isVisualStudio);
        }

        return locateInProgramFiles(isVisualStudio, VISUALSTUDIO_PATHS);
    }

    private Spec<File> isVisualStudio() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return new File(element, COMPILER_PATH + COMPILER_FILENAME).isFile();
            }
        };
    }

    public Search locateWindowsSdk(File candidate) {
        return locateInHierarchy(candidate, isWindowsSdk());
    }

    public Search locateDefaultWindowsSdk() {
        // If rc.exe is on the path, assume it is contained within a Windows SDK
        File resourceCompilerInPath = os.findInPath(RESOURCE_FILENAME);
        if (resourceCompilerInPath != null) {
            return locateInHierarchy(resourceCompilerInPath, isWindowsSdk());
        }

        return locateInProgramFiles(isWindowsSdk(), WINDOWSSDK_PATHS);
    }

    private Spec<File> isWindowsSdk() {
        return new Spec<File>() {
            public boolean isSatisfiedBy(File element) {
                return isWindowsSdk(element);
            }
        };
    }

    private boolean isWindowsSdk(File candidate) {
        return (new File(candidate, RESOURCE_PATH + RESOURCE_FILENAME).isFile() || new File(candidate, RESOURCE_PATH_WINSDK8 + RESOURCE_FILENAME).isFile())
            && (new File(candidate, KERNEL32_PATH + KERNEL32_FILENAME).isFile() || new File(candidate, KERNEL32_PATH_WINSDK8 + KERNEL32_FILENAME).isFile());
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

    private Search locateInProgramFiles(Spec<File> condition, VersionLookupPath... candidateLocations) {
        Search search = new Search();
        for (VersionLookupCandidate candidate : programFileCandidates(candidateLocations)) {
            if (condition.isSatisfiedBy(candidate.file)) {
                search.found(candidate);
                break;
            }
            search.notFound(candidate.file);
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

    private List<VersionLookupCandidate> programFileCandidates(VersionLookupPath... candidateDirectory) {
        List<VersionLookupCandidate> candidates = new ArrayList<VersionLookupCandidate>(candidateDirectory.length * 2);
        for (VersionLookupPath candidateLocation : candidateDirectory) {
            for (File file : programFileCandidates(candidateLocation.path)) {
                candidates.add(new VersionLookupCandidate(file, candidateLocation.version));
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

    private static class VersionLookupPath {
        String path;
        String version;

        public VersionLookupPath(String path, String version) {
            this.path = path;
            this.version = version;
        }

    }
 
    private static class VersionLookupCandidate {
        File file;
        String version;

        public VersionLookupCandidate(File file, String version) {
            this.file = file;
            this.version = version;
        }

    }

    public class Search implements SearchResult {
        private String version;
        private File file;
        private List<File> searchLocations = new ArrayList<File>();

        public boolean isFound() {
            return file != null;
        }

        public String getVersion() {
            return version;
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

        void found(VersionLookupCandidate candidate) {
            this.file = candidate.file;
            this.version = candidate.version;
        }
    }
}
