/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativecode.language.cpp.fixtures;

import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.toolchain.internal.gpp.version.GppVersionDeterminer;

import java.io.File;
import java.util.*;

public class AvailableToolChains {
    static List<ToolChainCandidate> getToolChains() {
        List<ToolChainCandidate> compilers = new ArrayList<ToolChainCandidate>();
        if (OperatingSystem.current().isWindows()) {
            compilers.add(findVisualCpp());
            compilers.add(findMinGW());
        } else {
            compilers.add(findGpp("3", "/opt/gcc/3.4.6/g++"));
            compilers.add(findGpp("4", null));
        }
        return compilers;
    }

    static private ToolChainCandidate findVisualCpp() {
        // Search first in path, then in the standard installation locations
        File compilerExe = OperatingSystem.current().findInPath("cl.exe");
        if (compilerExe != null) {
            return new InstalledToolChain("visual c++");
        }

        compilerExe = new File("C:/Program Files (x86)/Microsoft Visual Studio 10.0/VC/bin/cl.exe");
        if (compilerExe.isFile()) {
            File binDir = compilerExe.getParentFile();
            File vcDir = binDir.getParentFile();
            File baseDir = vcDir.getParentFile();
            File sdkDir = new File(baseDir.getParentFile(), "Microsoft SDKs/Windows/v7.0A");
            return new InstalledToolChain("visual c++",
                    new File(baseDir, "Common7/IDE"), 
                    binDir, 
                    new File(baseDir, "Common7/Tools"), 
                    new File(vcDir, "VCPackages"),
                    new File(sdkDir, "Bin"))
                    .envVar("INCLUDE", new File(vcDir, "include").getAbsolutePath())
                    .envVar("LIB", new File(vcDir, "lib").getAbsolutePath() + File.pathSeparator + new File(sdkDir, "lib").getAbsolutePath());
        }
        
        return new UnavailableToolChain("visual c++");
    }

    static private ToolChainCandidate findMinGW() {
        // Search in the standard installation locations (doesn't yet work with cygwin g++ in path)
        File compilerExe = new File("C:/MinGW/bin/g++.exe");
        if (compilerExe.isFile()) {
            return new InstalledToolChain("mingw", compilerExe.getParentFile());
        }

        return new UnavailableToolChain("mingw");
    }

    static private ToolChainCandidate findGpp(String versionPrefix, String hardcodedFallback) {
        String name = String.format("g++ (%s)", versionPrefix);
        GppVersionDeterminer versionDeterminer = new GppVersionDeterminer();
        for (File candidate : OperatingSystem.current().findAllInPath("g++")) {
            if (versionDeterminer.transform(candidate).startsWith(versionPrefix)) {
                return new InstalledToolChain(name, candidate.getParentFile());
            }
        }

        if (hardcodedFallback != null) {
            File fallback = new File(hardcodedFallback);
            if (fallback.isFile()) {
                return new InstalledToolChain(name, fallback.getParentFile());
            }
        }

        return new UnavailableToolChain(name);
    }

    public static abstract class ToolChainCandidate {
        @Override
        public String toString() {
            return getDisplayName();
        }

        public abstract String getDisplayName();
        
        public abstract boolean isAvailable();

        public abstract List<File> getPathEntries();

        public abstract Map<String, String> getEnvironmentVars();

        public boolean isVisualCpp() {
            return getDisplayName().equals("visual c++");
        }

        public boolean isGcc() {
            return !isVisualCpp();
        }
    }
    
    public static class InstalledToolChain extends ToolChainCandidate {
        private final List<File> pathEntries;
        private final Map<String, String> environmentVars = new HashMap<String, String>();
        private final String name;

        public InstalledToolChain(String name, File... pathEntries) {
            this.name = name;
            this.pathEntries = Arrays.asList(pathEntries);
        }

        InstalledToolChain envVar(String key, String value) {
            environmentVars.put(key, value);
            return this;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public List<File> getPathEntries() {
            return pathEntries;
        }

        @Override
        public Map<String, String> getEnvironmentVars() {
            return environmentVars;
        }
    }

    public static class UnavailableToolChain extends ToolChainCandidate {
        private final String name;

        public UnavailableToolChain(String name) {
            this.name = name;
        }

        @Override
        public String getDisplayName() {
            return name;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public List<File> getPathEntries() {
            throw new UnsupportedOperationException("This compiler is not installed.");
        }

        @Override
        public Map<String, String> getEnvironmentVars() {
            throw new UnsupportedOperationException("This compiler is not installed.");
        }
    }
}
