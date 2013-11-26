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
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.internal.ArchitectureNotationParser;
import org.gradle.nativebinaries.internal.DefaultPlatform;
import org.gradle.nativebinaries.internal.OperatingSystemNotationParser;
import org.gradle.nativebinaries.toolchain.Clang;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativebinaries.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.nativebinaries.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativebinaries.toolchain.plugins.ClangCompilerPlugin;
import org.gradle.nativebinaries.toolchain.plugins.GccCompilerPlugin;
import org.gradle.nativebinaries.toolchain.plugins.MicrosoftVisualCppPlugin;
import org.gradle.process.internal.DefaultExecAction;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.*;

public class AvailableToolChains {
    /**
     * @return A list of all tool chains installed on the system, with the default tool chain listed first (if installed).
     */
    public static List<InstalledToolChain> getAvailableToolChains() {
        List<ToolChainCandidate> allToolChains = getToolChains();
        List<InstalledToolChain> installedToolChains = new ArrayList<InstalledToolChain>();
        for (ToolChainCandidate candidate : allToolChains) {
            if (candidate.isAvailable()) {
                installedToolChains.add((InstalledToolChain) candidate);
            }
        }
        return installedToolChains;
    }

    /**
     * @return The tool chain with the given name.
     */
    public static ToolChainCandidate getToolChain(String name) {
        for (ToolChainCandidate toolChainCandidate : getToolChains()) {
            if (toolChainCandidate.getDisplayName().equals(name)) {
                return toolChainCandidate;
            }
        }
        return null;
    }

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
            compilers.add(findGcc("4", null));

            // Clang must be on the path
            // TODO:ADAM Also check on windows
            compilers.add(findClang());

            // TODO:DAZ Make a GCC3 install available for testing
        }
        return compilers;
    }

    static private ToolChainCandidate findClang() {
        File compilerExe = OperatingSystem.current().findInPath("clang");
        if (compilerExe != null) {
            return new InstalledClangToolChain();
        }
        return new UnavailableToolChain("clang");
    }

    static private ToolChainCandidate findVisualCpp() {
        // Search first in path, then in the standard installation locations
        File compilerExe = OperatingSystem.current().findInPath("cl.exe");
        if (compilerExe != null) {
            return new InstalledVisualCpp("visual c++");
        }

        VisualStudioLocator vsLocator = new DefaultVisualStudioLocator();
        VisualStudioLocator.SearchResult searchResult = vsLocator.locateDefaultVisualStudio();
        File visualStudioDir = searchResult.getResult();
        if (visualStudioDir != null) {
            VisualStudioInstall install = new VisualStudioInstall(visualStudioDir, searchResult.getVersion());
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
            return new InstalledGcc("gcc cygwin").inPath(compilerExe.getParentFile());
        }

        return new UnavailableToolChain("gcc cygwin");
    }

    static private ToolChainCandidate findGcc(String versionPrefix, String hardcodedFallback) {
        String name = String.format("gcc %s", versionPrefix);
        GccVersionDeterminer versionDeterminer = new GccVersionDeterminer(new ExecActionFactory() {
            public ExecAction newExecAction() {
                return new DefaultExecAction(new IdentityFileResolver());
            }
        });

        List<File> gppCandidates = OperatingSystem.current().findAllInPath("g++");
        for (int i = 0; i < gppCandidates.size(); i++) {
            File candidate = gppCandidates.get(i);
            String version = versionDeterminer.transform(candidate);
            if (version != null && version.startsWith(versionPrefix)) {
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

   }
    
    public abstract static class InstalledToolChain extends ToolChainCandidate {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServices.getInstance().get(ProcessEnvironment.class);
        protected final List<File> pathEntries = new ArrayList<File>();
        private final String displayName;
        private final String pathVarName;
        private String originalPath;

        public InstalledToolChain(String displayName) {
            this.displayName = displayName;
            this.pathVarName = OperatingSystem.current().getPathVar();
        }

        InstalledToolChain inPath(File... pathEntries) {
            Collections.addAll(this.pathEntries, pathEntries);
            return this;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        public String getTypeDisplayName() {
            return getDisplayName().replaceAll("\\s+\\d+(\\.\\d+)*$", "");
        }

        public ExecutableFixture executable(Object path) {
            return new ExecutableFixture(new TestFile(OperatingSystem.current().getExecutableName(path.toString())), this);
        }

        public TestFile objectFile(Object path) {
            return new TestFile(path.toString() + ".o");
        }

        public SharedLibraryFixture sharedLibrary(Object path) {
            return new SharedLibraryFixture(new TestFile(OperatingSystem.current().getSharedLibraryName(path.toString())), this);
        }

        public StaticLibraryFixture staticLibrary(Object path) {
            return new StaticLibraryFixture(new TestFile(OperatingSystem.current().getStaticLibraryName(path.toString())), this);
        }

        public NativeBinaryFixture resourceOnlyLibrary(Object path) {
            return new NativeBinaryFixture(new TestFile(OperatingSystem.current().getSharedLibraryName(path.toString())), this);
        }

        public void initialiseEnvironment() {
            String compilerPath = Joiner.on(File.pathSeparator).join(pathEntries);

            if (compilerPath.length() > 0) {
                originalPath = System.getenv(pathVarName);
                String path = compilerPath + File.pathSeparator + originalPath;
                System.out.println(String.format("Using path %s", path));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, path);
            }
        }

        public void resetEnvironment() {
            if (originalPath != null) {
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, originalPath);
            }
        }

        public abstract String getBuildScriptConfig();

        public abstract String getImplementationClass();

        public abstract String getPluginClass();

        public boolean isVisualCpp() {
            return false;
        }

        public List<File> getPathEntries() {
            return pathEntries;
        }

        /**
         * The environment required to execute a binary created by this toolchain.
         */
        public List<String> getRuntimeEnv() {
            if (pathEntries.isEmpty()) {
                return Collections.emptyList();
            }

            String path = Joiner.on(File.pathSeparator).join(pathEntries) + File.pathSeparator + System.getenv(pathVarName);
            return Collections.singletonList(pathVarName + "=" + path);
        }

        public String getId() {
            return displayName.replaceAll("\\W", "");
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
                config += String.format("%s.path file('%s')", getId(), pathEntry.toURI());
            }
            return config;
        }

        public String getImplementationClass() {
            return Gcc.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return GccCompilerPlugin.class.getSimpleName();
        }
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private String version;
        private File installDir;

        public InstalledVisualCpp(String name) {
            super(name);
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            DefaultPlatform targetPlatform = new DefaultPlatform("default", ArchitectureNotationParser.parser(), OperatingSystemNotationParser.parser());
            installDir = install.getVisualStudioDir();
            version = install.getVisualStudioVersion();
            pathEntries.add(install.getVisualCppBin(targetPlatform));
            pathEntries.add(install.getCommonIdeBin());
            return this;
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            if (installDir != null) {
                config += String.format("%s.installDir = file('%s')", getId(), installDir.toURI());
            }
            return config;
        }

        public String getImplementationClass() {
            return VisualCpp.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return MicrosoftVisualCppPlugin.class.getSimpleName();
        }

        public boolean isVisualCpp() {
            return true;
        }

        public String getVersion() {
            return version;
        }

        @Override
        public TestFile objectFile(Object path) {
            return new TestFile(path.toString() + ".obj");
        }
    }

    private static class InstalledClangToolChain extends InstalledToolChain {
        public InstalledClangToolChain() {
            super("clang");
        }

        @Override
        public String getBuildScriptConfig() {
            return "clang(Clang)";
        }

        @Override
        public String getImplementationClass() {
            return Clang.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return ClangCompilerPlugin.class.getSimpleName();
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
