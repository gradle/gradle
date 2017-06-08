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

package org.gradle.nativeplatform.fixtures;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.Nullable;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.specs.Spec;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.swift.plugins.SwiftExecutablePlugin;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.Swiftc;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionDeterminer;
import org.gradle.nativeplatform.toolchain.internal.gcc.version.GccVersionResult;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativeplatform.toolchain.plugins.ClangCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.GccCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.MicrosoftVisualCppCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.CollectionUtils;
import org.gradle.util.VersionNumber;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static org.gradle.nativeplatform.fixtures.VisualStudioVersion.VISUALSTUDIO_2012;
import static org.gradle.nativeplatform.fixtures.VisualStudioVersion.VISUALSTUDIO_2013;
import static org.gradle.nativeplatform.fixtures.VisualStudioVersion.VISUALSTUDIO_2015;

public class AvailableToolChains {
    private static List<ToolChainCandidate> toolChains;

    /**
     * Locates the tool chain that would be used as the default for the current machine, if any.
     * @return null if there is no such tool chain.
     */
    @Nullable
    public static InstalledToolChain getDefaultToolChain() {
        for (ToolChainCandidate toolChain : getToolChains()) {
            if (toolChain.isAvailable()) {
                return (InstalledToolChain) toolChain;
            }
        }
        return null;
    }

    /**
     * Locates a tool chain that meets the given criteria, if any.
     *
     * @return null if there is no such tool chain.
     */
    @Nullable
    public static ToolChainCandidate getToolChain(ToolChainRequirement requirement) {
        for (ToolChainCandidate toolChainCandidate : getToolChains()) {
            if (toolChainCandidate.meets(requirement)) {
                return toolChainCandidate;
            }
        }
        return null;
    }

    /**
     * @return A list of all known tool chains for this platform. Includes those tool chains that are not available on the current machine.
     */
    public static List<ToolChainCandidate> getToolChains() {
        if (toolChains == null) {
            List<ToolChainCandidate> compilers = new ArrayList<ToolChainCandidate>();
            if (OperatingSystem.current().isWindows()) {
                compilers.addAll(findVisualCpps());
                compilers.add(findMinGW());
                compilers.add(findCygwin());
            } else {
                compilers.add(findGcc());
                compilers.add(findClang());
                compilers.add(findSwiftc());
            }
            toolChains = compilers;
        }
        return toolChains;
    }

    static private ToolChainCandidate findClang() {
        File compilerExe = OperatingSystem.current().findInPath("clang");
        if (compilerExe != null) {
            return new InstalledClang();
        }
        return new UnavailableToolChain("clang");
    }

    static private boolean isTestableVisualStudioVersion(final VersionNumber version) {
        return getVisualStudioVersion(version) != null;
    }

    static private VisualStudioVersion getVisualStudioVersion(final VersionNumber version) {
        return CollectionUtils.findFirst(VisualStudioVersion.values(), new Spec<VisualStudioVersion>() {
            @Override
            public boolean isSatisfiedBy(VisualStudioVersion candidate) {
                return candidate.getVisualCppVersion().equals(version);
            }
        });
    }

    static private List<ToolChainCandidate> findVisualCpps() {
        // Search in the standard installation locations
        VisualStudioLocator vsLocator = new DefaultVisualStudioLocator(OperatingSystem.current(), NativeServicesTestFixture.getInstance().get(WindowsRegistry.class), NativeServicesTestFixture.getInstance().get(SystemInfo.class));
        final List<VisualStudioLocator.SearchResult> searchResults = vsLocator.locateAllVisualStudioVersions();

        List<ToolChainCandidate> toolChains = Lists.newArrayList();

        for (VisualStudioLocator.SearchResult searchResult : searchResults) {
            if (searchResult.isAvailable()) {
                VisualStudioInstall install = searchResult.getVisualStudio();
                if (isTestableVisualStudioVersion(install.getVersion())) {
                    toolChains.add(new InstalledVisualCpp(getVisualStudioVersion(install.getVersion())).withInstall(install));
                }
            }
        }

        if (toolChains.isEmpty()) {
            toolChains.add(new UnavailableToolChain("visual c++"));
        }

        return toolChains;
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

    static private ToolChainCandidate findGcc() {
        GccVersionDeterminer versionDeterminer = GccVersionDeterminer.forGcc(TestFiles.execActionFactory());

        List<File> gppCandidates = OperatingSystem.current().findAllInPath("g++");
        for (int i = 0; i < gppCandidates.size(); i++) {
            File candidate = gppCandidates.get(i);
            GccVersionResult version = versionDeterminer.getGccMetaData(candidate, Collections.<String>emptyList());
            if (version.isAvailable()) {
                InstalledGcc gcc = new InstalledGcc("gcc");
                if (i > 0) {
                    // Not the first g++ in the path, needs the path variable updated
                    gcc.inPath(candidate.getParentFile());
                }
                return gcc;
            }
        }

        return new UnavailableToolChain("gcc");
    }

    static private ToolChainCandidate findSwiftc() {
        List<File> swiftcCandidates = OperatingSystem.current().findAllInPath("swiftc");
        if (!swiftcCandidates.isEmpty()) {
            return new InstalledSwiftc("swiftc");
        }

        return new UnavailableToolChain("swiftc");
    }

    public static abstract class ToolChainCandidate {
        @Override
        public String toString() {
            return getDisplayName();
        }

        public abstract String getDisplayName();

        public abstract boolean isAvailable();

        public abstract boolean meets(ToolChainRequirement requirement);

        public abstract boolean meets(EnumSet<NativeLanguageRequirement> requirement);

        public abstract void initialiseEnvironment();

        public abstract void resetEnvironment();

   }

    public abstract static class InstalledToolChain extends ToolChainCandidate {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServicesTestFixture.getInstance().get(ProcessEnvironment.class);
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
            return getDisplayName().replaceAll("\\s+\\d+(\\.\\d+)*(\\s+\\(\\d+(\\.\\d+)*\\))?$", "");
        }

        public abstract String getInstanceDisplayName();

        public ExecutableFixture executable(Object path) {
            return new ExecutableFixture(new TestFile(OperatingSystem.current().getExecutableName(path.toString())), this);
        }

        public LinkerOptionsFixture linkerOptionsFor(Object path) {
            return new LinkerOptionsFixture(new TestFile(path.toString()));
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

        public abstract String getUnitTestPlatform();
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

        @Override
        public boolean meets(EnumSet<NativeLanguageRequirement> requirement) {
            return EnumSet.of(NativeLanguageRequirement.C_PLUS_PLUS, NativeLanguageRequirement.C, NativeLanguageRequirement.ASSEMBLY,
                NativeLanguageRequirement.OBJECTIVE_C, NativeLanguageRequirement.OBJECTIVE_C_PLUS_PLUS).containsAll(requirement);
        }

        public String getLinker() {
            return getCCompiler();
        }

        public String getStaticLibArchiver() {
            return find("ar");
        }

        public abstract String getCCompiler();

        @Override
        public String getUnitTestPlatform() {
            if (OperatingSystem.current().isMacOsX()) {
                return "osx";
            }
            if (OperatingSystem.current().isLinux()) {
                return "linux";
            }
            return "UNKNOWN";
        }
    }

    public static class InstalledGcc extends GccCompatibleToolChain {
        public InstalledGcc(String name) {
            super(name);
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.GCC || requirement == ToolChainRequirement.GCC_COMPATIBLE || requirement == ToolChainRequirement.AVAILABLE;
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

        @Override
        public String getUnitTestPlatform() {
            if ("mingw".equals(getDisplayName())) {
                return "mingw";
            }
            if ("gcc cygwin".equals(getDisplayName())) {
                return "cygwin";
            }
            return "UNKNOWN";
        }
    }

    public static class InstalledSwiftc extends InstalledToolChain {
        public InstalledSwiftc(String displayName) {
            super(displayName);
        }

        @Override
        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (Swiftc)", getId());
        }

        @Override
        public String getBuildScriptConfig() {
            return "swiftc(Swiftc)";
        }

        @Override
        public String getImplementationClass() {
            return Swiftc.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return SwiftCompilerPlugin.class.getSimpleName();
        }

        @Override
        public String getUnitTestPlatform() {
            return null;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.SWIFT || requirement == ToolChainRequirement.AVAILABLE;
        }

        @Override
        public boolean meets(EnumSet<NativeLanguageRequirement> requirement) {
            return requirement.contains(NativeLanguageRequirement.SWIFT);
        }
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private VersionNumber version;
        private File installDir;

        public InstalledVisualCpp(VisualStudioVersion version) {
            super("visual c++ " + version.getVersion() + " (" + version.getVisualCppVersion().toString() + ")");
        }

        @Override
        public String getId() {
            return "visualCpp";
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            DefaultNativePlatform targetPlatform = new DefaultNativePlatform("default");
            installDir = install.getVisualStudioDir();
            version = install.getVersion();
            pathEntries.addAll(install.getVisualCpp().getPath(targetPlatform));
            return this;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            switch (requirement) {
                case AVAILABLE:
                case VISUALCPP:
                    return true;
                case VISUALCPP_2012_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2012.getVisualCppVersion()) >= 0;
                case VISUALCPP_2013:
                    return version.equals(VISUALSTUDIO_2013.getVisualCppVersion());
                case VISUALCPP_2013_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2013.getVisualCppVersion()) >= 0;
                case VISUALCPP_2015:
                    return version.equals(VISUALSTUDIO_2015.getVisualCppVersion());
                case VISUALCPP_2015_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2015.getVisualCppVersion()) >= 0;
                default:
                    return false;
            }
        }

        @Override
        public boolean meets(EnumSet<NativeLanguageRequirement> requirement) {
            return EnumSet.of(NativeLanguageRequirement.C_PLUS_PLUS, NativeLanguageRequirement.C, NativeLanguageRequirement.ASSEMBLY,
                NativeLanguageRequirement.WINDOWS_RESOURCE).containsAll(requirement);
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
            return MicrosoftVisualCppCompilerPlugin.class.getSimpleName();
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

        @Override
        public String getUnitTestPlatform() {
            switch (version.getMajor()) {
                case 12:
                    return "vs2013";
                case 14:
                    return "vs2015";
                default:
                    return "UNKNOWN";
            }
        }
    }

    public static class InstalledClang extends GccCompatibleToolChain {
        public InstalledClang() {
            super("clang");
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.CLANG || requirement == ToolChainRequirement.GCC_COMPATIBLE || requirement == ToolChainRequirement.AVAILABLE;
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
        public boolean meets(EnumSet<NativeLanguageRequirement> requirement) {
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
