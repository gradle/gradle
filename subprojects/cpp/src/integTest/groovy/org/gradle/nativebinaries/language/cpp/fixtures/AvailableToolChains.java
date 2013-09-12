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

package org.gradle.nativebinaries.language.cpp.fixtures;

import com.google.common.base.Joiner;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.DefaultPlatform;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.ToolRegistry;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioLocation;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.util.TextUtil;

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
            // GCC4.x must be on the path
            compilers.add(findGpp("4", null));
            // CI servers have GCC4.4 installed additionally
            compilers.add(findGpp("4.4", "/opt/gcc/4.4/bin/g++"));

            // It's easy to get GCC4.8 installed on OSX, and symlink to this location
            // Not available on CI servers, so only add it if it's available
            ToolChainCandidate gpp48 = findGpp("4.8", "/opt/gcc/4.8/bin/g++");
            if (gpp48.isAvailable()) {
                compilers.add(gpp48);
            }

            // TODO:DAZ Make a GCC3 install available for testing
        }
        return compilers;
    }

    static private ToolChainCandidate findVisualCpp() {
        // Search first in path, then in the standard installation locations
        File compilerExe = OperatingSystem.current().findInPath("cl.exe");
        if (compilerExe != null) {
            return new InstalledVisualCpp("visual c++");
        }

        VisualStudioLocation vsLocation = VisualStudioLocation.findDefault();
        if (vsLocation.isFound()) {
            VisualStudioInstall install = new VisualStudioInstall(vsLocation.getVisualStudioDir(), vsLocation.getWindowsSdkDir());
            return new InstalledVisualCpp("visual c++").withInstall(install);
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
        GccVersionDeterminer versionDeterminer = new GccVersionDeterminer();

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
            this.pathVarName = OperatingSystem.current().getPathVar();
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

        public abstract String getImplementationClass();

        public boolean isVisualCpp() {
            return false;
        }

        public List<File> getPathEntries() {
            return pathEntries;
        }

        /**
         * The environment required to execute a binary created by this toolchain.
         */
        // TODO:DAZ This isn't quite right (only required for MinGW and cygwin, and preferably not even those)
        public List<String> getRuntimeEnv() {
            if (pathEntries.isEmpty()) {
                return Collections.emptyList();
            }

            String path = Joiner.on(File.pathSeparator).join(pathEntries) + File.pathSeparator + System.getenv(pathVarName);
            return Collections.singletonList(pathVarName + "=" + path);
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
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')", getId(), TextUtil.normaliseFileSeparators(pathEntry.getAbsolutePath()));
            }
            return config;
        }

        public String getImplementationClass() {
            return Gcc.class.getSimpleName();
        }
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private File installDir;

        public InstalledVisualCpp(String name) {
            super(name);
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            ToolRegistry toolRegistry = new ToolRegistry(OperatingSystem.current());
            DefaultPlatform targetPlatform = new DefaultPlatform("default");
            install.configureTools(toolRegistry, targetPlatform);
            installDir = install.getVisualStudioDir();
            pathEntries.addAll(toolRegistry.getPath());
            environmentVars.putAll(toolRegistry.getEnvironment());
            return this;
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            if (installDir != null) {
                config += String.format("%s.installDir = file('%s')", getId(), TextUtil.normaliseFileSeparators(installDir.getAbsolutePath()));
            }
            return config;
        }

        public String getImplementationClass() {
            return VisualCpp.class.getSimpleName();
        }

        public boolean isVisualCpp() {
            return true;
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
