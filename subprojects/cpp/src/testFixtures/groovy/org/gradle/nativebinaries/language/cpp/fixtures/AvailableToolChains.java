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
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.platform.internal.DefaultPlatform;
import org.gradle.nativebinaries.toolchain.Clang;
import org.gradle.nativebinaries.toolchain.Gcc;
import org.gradle.nativebinaries.toolchain.VisualCpp;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativebinaries.toolchain.internal.gcc.version.GccVersionResult;
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
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
    public static ToolChainCandidate getToolChain(ToolChainRequirement requirement) {
        for (ToolChainCandidate toolChainCandidate : getToolChains()) {
            if (toolChainCandidate.meets(requirement)) {
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
            return new InstalledClang();
        }
        return new UnavailableToolChain("clang");
    }

    static private ToolChainCandidate findVisualCpp() {
        // Search in the standard installation locations
        VisualStudioLocator vsLocator = new DefaultVisualStudioLocator(OperatingSystem.current(), NativeServices.getInstance().get(WindowsRegistry.class), NativeServices.getInstance().get(SystemInfo.class));
        VisualStudioLocator.SearchResult searchResult = vsLocator.locateVisualStudioInstalls(null);
        if (searchResult.isAvailable()) {
            VisualStudioInstall install = searchResult.getVisualStudio();
            return new InstalledVisualCpp("visual c++").withInstall(install);
        }

        return new UnavailableToolChain("visual c++");
    }

    static private ToolChainCandidate findMinGW() {
        // Search in the standard installation locations
        File compilerExe = new File("C:/MinGW/bin/g++.exe");
        if (compilerExe.isFile()) {
            return new InstalledWindowsGcc("mingw").inPath(compilerExe.getParentFile());
        }

        return new UnavailableToolChain("mingw");
    }

    static private ToolChainCandidate findCygwin() {
        // Search in the standard installation locations
        File compilerExe = new File("C:/cygwin/bin/g++.exe");
        if (compilerExe.isFile()) {
            return new InstalledWindowsGcc("gcc cygwin").inPath(compilerExe.getParentFile());
        }

        return new UnavailableToolChain("gcc cygwin");
    }

    static private ToolChainCandidate findGcc(String versionPrefix, String hardcodedFallback) {
        String name = String.format("gcc %s", versionPrefix);
        GccVersionDeterminer versionDeterminer = new GccVersionDeterminer(new ExecActionFactory() {
            public ExecAction newExecAction() {
                return new DefaultExecAction(TestFiles.resolver());
            }
        });

        List<File> gppCandidates = OperatingSystem.current().findAllInPath("g++");
        for (int i = 0; i < gppCandidates.size(); i++) {
            File candidate = gppCandidates.get(i);
            GccVersionResult version = versionDeterminer.transform(candidate);
            if (version.isAvailable() && version.getVersion().startsWith(versionPrefix)) {
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

        public abstract boolean meets(ToolChainRequirement requirement);

        public abstract void initialiseEnvironment();

        public abstract void resetEnvironment();

   }
    
    public abstract static class InstalledToolChain extends ToolChainCandidate {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServices.getInstance().get(ProcessEnvironment.class);
        protected final List<File> pathEntries = new ArrayList<File>();
        private final String displayName;
        protected final String pathVarName;
        private final String objectFileNameSuffix;

        private String originalPath;

        public InstalledToolChain(String displayName) {
            this.displayName = displayName;
            this.pathVarName = OperatingSystem.current().getPathVar();
            this.objectFileNameSuffix = OperatingSystem.current().isWindows() ? ".obj" : ".o";
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

        public abstract String getInstanceDisplayName();

        public ExecutableFixture executable(Object path) {
            return new ExecutableFixture(new TestFile(OperatingSystem.current().getExecutableName(path.toString())), this);
        }

        public TestFile objectFile(Object path) {
            return new TestFile(path.toString() + objectFileNameSuffix);
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

        /**
         * Initialise the process environment so that this tool chain is visible to the default discovery mechanism that the
         * plugin uses (eg add the compiler to the PATH).
         */
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
            // Toolchains should be linking against stuff in the standard locations
            return Collections.emptyList();
        }

        public String getId() {
            return displayName.replaceAll("\\W", "");
        }
    }

    public static abstract class GccCompatibleToolChain extends InstalledToolChain {
        protected GccCompatibleToolChain(String displayName) {
            super(displayName);
        }

        protected String find(String tool) {
            if (getPathEntries().isEmpty()) {
                return tool;
            }
            return new File(getPathEntries().get(0), tool).getAbsolutePath();
        }

        public String getLinker() {
            return getCCompiler();
        }

        public String getStaticLibArchiver() {
            return find("ar");
        }

        public abstract String getCCompiler();
    }

    public static class InstalledGcc extends GccCompatibleToolChain {
        public InstalledGcc(String name) {
            super(name);
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.Gcc || requirement == ToolChainRequirement.GccCompatible || requirement == ToolChainRequirement.Available;
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')", getId(), pathEntry.toURI());
            }
            return config;
        }

        @Override
        public String getCCompiler() {
            return find("gcc");
        }

        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (GNU GCC)", getId());
        }

        public String getImplementationClass() {
            return Gcc.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return GccCompilerPlugin.class.getSimpleName();
        }
    }

    public static class InstalledWindowsGcc extends InstalledGcc {
        public InstalledWindowsGcc(String name) {
            super(name);
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
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private VersionNumber version;
        private File installDir;

        public InstalledVisualCpp(String name) {
            super(name);
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            DefaultPlatform targetPlatform = new DefaultPlatform("default");
            installDir = install.getVisualStudioDir();
            version = install.getVersion();
            pathEntries.addAll(install.getVisualCpp().getPath(targetPlatform));
            return this;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            switch (requirement) {
                case Available:
                case VisualCpp:
                    return true;
                case VisualCpp2013:
                    return version.compareTo(VersionNumber.parse("12.0")) >= 0;
                default:
                    return false;
            }
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

        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (Visual Studio)", getId());
        }

        @Override
        public String getPluginClass() {
            return MicrosoftVisualCppPlugin.class.getSimpleName();
        }

        public boolean isVisualCpp() {
            return true;
        }

        public VersionNumber getVersion() {
            return version;
        }

        @Override
        public TestFile objectFile(Object path) {
            return new TestFile(path.toString() + ".obj");
        }
    }

    public static class InstalledClang extends GccCompatibleToolChain {
        public InstalledClang() {
            super("clang");
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.GccCompatible || requirement == ToolChainRequirement.Available;
        }

        @Override
        public String getBuildScriptConfig() {
            return "clang(Clang)";
        }

        @Override
        public String getCCompiler() {
            return find("clang");
        }

        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (Clang)", getId());
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
        public boolean meets(ToolChainRequirement requirement) {
            return false;
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
