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

import com.google.common.base.Joiner;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativecode.toolchain.internal.gpp.GppToolChain;
import org.gradle.nativecode.toolchain.internal.gpp.version.GppVersionDeterminer;
import org.gradle.nativecode.toolchain.internal.msvcpp.VisualCppToolChain;
import org.gradle.nativecode.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.*;

public class AvailableToolChains {
    /**
     * @return A list of all tool chains for this platform, with the default tool chain listed first.
     */
    public static List<ToolChainCandidate> getToolChains() {
        List<ToolChainCandidate> compilers = new ArrayList<ToolChainCandidate>();
        if (OperatingSystem.current().isWindows()) {
            compilers.add(findVisualCpp());
            compilers.add(findMinGW());
            compilers.add(findCygwin());
        } else {
            compilers.add(findGpp("4", null));
            compilers.add(findGpp("3", "/opt/gcc/3.4.6/bin/g++"));

            // TODO:DAZ This is here for local testing
            compilers.add(findGpp("4.8", "/usr/local/gcc-4.8/g++"));
        }
        return compilers;
    }

    static private ToolChainCandidate findVisualCpp() {
        // Search first in path, then in the standard installation locations
        File compilerExe = OperatingSystem.current().findInPath("cl.exe");
        if (compilerExe != null) {
            return new InstalledVisualCpp("visual c++");
        }

        VisualStudioInstall install = new VisualStudioInstall(new File("C:/Program Files (x86)/Microsoft Visual Studio 10.0"));
        if (install.isInstalled()) {
            InstalledVisualCpp visualCpp = new InstalledVisualCpp("visual c++").withInstall(install);

            return visualCpp;
        }
        
        return new UnavailableToolChain("visual c++");
    }

    static private ToolChainCandidate findMinGW() {
        // Search in the standard installation locations
        File compilerExe = new File("C:/MinGW/bin/g++.exe");
        if (compilerExe.isFile()) {
            return new InstalledGcc("mingw").inPath(compilerExe.getParentFile());
        }

        return new UnavailableToolChain("mingw");
    }

    static private ToolChainCandidate findCygwin() {
        // Search in the standard installation locations
        File compilerExe = new File("C:/cygwin/bin/g++.exe");
        if (compilerExe.isFile()) {
            return new InstalledGcc("g++ cygwin").inPath(compilerExe.getParentFile());
        }

        return new UnavailableToolChain("g++ cygwin");
    }

    static private ToolChainCandidate findGpp(String versionPrefix, String hardcodedFallback) {
        String name = String.format("g++ %s", versionPrefix);
        GppVersionDeterminer versionDeterminer = new GppVersionDeterminer();

        List<File> gppCandidates = OperatingSystem.current().findAllInPath("g++");
        for (int i = 0; i < gppCandidates.size(); i++) {
            File candidate = gppCandidates.get(i);
            if (versionDeterminer.transform(candidate).startsWith(versionPrefix)) {
                InstalledGcc gcc = new InstalledGcc(name);
                if (i > 0) {
                    // Not the first g++ in the path, needs the path variable updated
                    gcc.inPath(candidate.getParentFile());
                }
                return gcc;
            }
        }

        if (hardcodedFallback != null) {
            File fallback = new File(hardcodedFallback);
            if (fallback.isFile()) {
                return new InstalledGcc(name).inPath(fallback.getParentFile());
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

        public abstract void initialiseEnvironment();

        public abstract void resetEnvironment();

        public boolean isVisualCpp() {
            return getDisplayName().equals("visual c++");
        }

        public ExecutableFixture executable(Object path) {
            return new ExecutableFixture(new TestFile(OperatingSystem.current().getExecutableName(path.toString())), this);
        }

        public SharedLibraryFixture sharedLibrary(Object path) {
            return new SharedLibraryFixture(new TestFile(OperatingSystem.current().getSharedLibraryName(path.toString())), this);
        }

        public NativeBinaryFixture staticLibrary(Object path) {
            return new NativeBinaryFixture(new TestFile(OperatingSystem.current().getStaticLibraryName(path.toString())), this);
        }
    }
    
    public abstract static class InstalledToolChain extends ToolChainCandidate {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServices.getInstance().get(ProcessEnvironment.class);
        protected final List<File> pathEntries = new ArrayList<File>();
        protected final Map<String, String> environmentVars = new HashMap<String, String>();
        private final String name;
        private final String pathVarName;
        private String originalPath;

        public InstalledToolChain(String name) {
            this.name = name;
            this.pathVarName = !OperatingSystem.current().isWindows() ? "Path" : "PATH";
        }

        InstalledToolChain inPath(File... pathEntries) {
            Collections.addAll(this.pathEntries, pathEntries);
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

        public void initialiseEnvironment() {
            String compilerPath = Joiner.on(File.pathSeparator).join(pathEntries);

            if (compilerPath.length() > 0) {
                originalPath = System.getenv(pathVarName);
                String path = compilerPath + File.pathSeparator + originalPath;
                System.out.println(String.format("Using path %s", path));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, path);
            }

            for (Map.Entry<String, String> entry : environmentVars.entrySet()) {
                System.out.println(String.format("Using environment var %s -> %s", entry.getKey(), entry.getValue()));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(entry.getKey(), entry.getValue());
            }
        }

        public void resetEnvironment() {
            if (originalPath != null) {
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, originalPath);
            }
        }

        public abstract String getBuildScriptConfig();

        public String getImplementationClass() {
            return isVisualCpp() ? VisualCppToolChain.class.getName() : GppToolChain.class.getName();
        }

        public List<File> getPathEntries() {
            return pathEntries;
        }

        public String getId() {
            return name.replaceAll("\\W", "");
        }
    }

    public static class InstalledGcc extends InstalledToolChain {
        public InstalledGcc(String name) {
            super(name);
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), GppToolChain.class.getName());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')", getId(), pathEntry.getAbsolutePath());
            }
            return config;
        }
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private File installDir;

        public InstalledVisualCpp(String name) {
            super(name);
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), VisualCppToolChain.class.getName());
            if (installDir != null) {
                config += String.format("%s.installDir = file('%s')", getId(), installDir.getAbsolutePath());
            }
            return config;
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            pathEntries.addAll(install.getPathEntries());
            environmentVars.putAll(install.getEnvironment());
            installDir = install.getInstallDir();
            return this;
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
        public void initialiseEnvironment() {
            throw new UnsupportedOperationException("Toolchain is not available");
        }

        @Override
        public void resetEnvironment() {
            throw new UnsupportedOperationException("Toolchain is not available");
        }
    }
}
