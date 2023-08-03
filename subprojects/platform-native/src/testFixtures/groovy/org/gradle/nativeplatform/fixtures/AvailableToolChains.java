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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.gradle.api.internal.file.TestFiles;
import org.gradle.api.specs.Spec;
import org.gradle.integtests.fixtures.VersionedTool;
import org.gradle.integtests.fixtures.executer.GradleExecuter;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioLocatorTestFixture;
import org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.Clang;
import org.gradle.nativeplatform.toolchain.Gcc;
import org.gradle.nativeplatform.toolchain.Swiftc;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioInstall;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadataProvider;
import org.gradle.nativeplatform.toolchain.plugins.ClangCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.GccCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.MicrosoftVisualCppCompilerPlugin;
import org.gradle.nativeplatform.toolchain.plugins.SwiftCompilerPlugin;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.testfixtures.internal.NativeServicesTestFixture;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.GUtil;
import org.gradle.util.internal.VersionNumber;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion.VISUALSTUDIO_2012;
import static org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion.VISUALSTUDIO_2013;
import static org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion.VISUALSTUDIO_2015;
import static org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion.VISUALSTUDIO_2017;
import static org.gradle.nativeplatform.fixtures.msvcpp.VisualStudioVersion.VISUALSTUDIO_2019;

public class AvailableToolChains {
    private static final Comparator<ToolChainCandidate> LATEST_RELEASED_FIRST = Collections.reverseOrder(new Comparator<ToolChainCandidate>() {
        @Override
        public int compare(ToolChainCandidate toolchain1, ToolChainCandidate toolchain2) {
            return toolchain1.getVersion().compareTo(toolchain2.getVersion());
        }
    });
    private static List<ToolChainCandidate> toolChains;

    /**
     * Locates the tool chain that would be used as the default for the current machine, if any.
     *
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
    public static InstalledToolChain getToolChain(ToolChainRequirement requirement) {
        for (ToolChainCandidate toolChainCandidate : getToolChains()) {
            if (toolChainCandidate.meets(requirement)) {
                assert toolChainCandidate.isAvailable();
                return (InstalledToolChain) toolChainCandidate;
            }
        }
        return null;
    }

    /**
     * @return A list of all known tool chains for this platform. Includes those tool chains that are not available on the current machine.
     */
    public static List<ToolChainCandidate> getToolChains() {
        if (toolChains == null) {
            List<ToolChainCandidate> compilers = new ArrayList<>();
            if (OperatingSystem.current().isWindows()) {
                compilers.addAll(findVisualCpps());
                compilers.add(findMinGW());
            } else if (OperatingSystem.current().isMacOsX()) {
                compilers.addAll(findClangs(true));
                compilers.addAll(findGccs(false));
                compilers.addAll(findSwiftcs());
            } else {
                compilers.addAll(findGccs(true));
                compilers.addAll(findClangs(false));
                compilers.addAll(findSwiftcs());
            }
            toolChains = compilers;
        }
        return toolChains;
    }

    static private List<ToolChainCandidate> findClangs(boolean mustFind) {
        List<ToolChainCandidate> toolChains = Lists.newArrayList();

        // On macOS, we assume co-located Xcode is installed into /opt/xcode and default location at /Applications/Xcode.app
        //   We need to search for Clang differently on macOS because we need to know the Xcode version for x86 support.
        if (OperatingSystem.current().isMacOsX()) {
            toolChains.addAll(findXcodes().stream().map(InstalledXcode::getClang).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));
        } else {
            GccMetadataProvider versionDeterminer = GccMetadataProvider.forClang(TestFiles.execActionFactory());
            Set<File> clangCandidates = ImmutableSet.copyOf(OperatingSystem.current().findAllInPath("clang"));
            if (!clangCandidates.isEmpty()) {
                File firstInPath = clangCandidates.iterator().next();
                for (File candidate : clangCandidates) {
                    SearchResult<GccMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(candidate));
                    if (version.isAvailable()) {
                        InstalledClang clang = new InstalledClang(version.getComponent().getVersion());
                        if (!candidate.equals(firstInPath)) {
                            // Not the first g++ in the path, needs the path variable updated
                            clang.inPath(candidate.getParentFile());
                        }
                        toolChains.add(clang);
                    }
                }
            }
        }

        if (mustFind && toolChains.isEmpty()) {
            toolChains.add(new UnavailableToolChain(ToolFamily.CLANG));
        }

        toolChains.sort(LATEST_RELEASED_FIRST);

        return toolChains;
    }

    static private boolean isTestableVisualStudioVersion(final VersionNumber version) {
        return getVisualStudioVersion(version) != null;
    }

    static private VisualStudioVersion getVisualStudioVersion(final VersionNumber version) {
        return CollectionUtils.findFirst(VisualStudioVersion.values(), new Spec<VisualStudioVersion>() {
            @Override
            public boolean isSatisfiedBy(VisualStudioVersion candidate) {
                return candidate.getVersion().getMajor() == version.getMajor();
            }
        });
    }

    static private List<ToolChainCandidate> findVisualCpps() {
        // Search in the standard installation locations
        final List<? extends VisualStudioInstall> searchResults = VisualStudioLocatorTestFixture.getVisualStudioLocator().locateAllComponents();

        List<ToolChainCandidate> toolChains = Lists.newArrayList();

        for (VisualStudioInstall install : searchResults) {
            if (isTestableVisualStudioVersion(install.getVersion())) {
                toolChains.add(new InstalledVisualCpp(getVisualStudioVersion(install.getVersion())).withInstall(install));
            }
        }

        if (toolChains.isEmpty()) {
            toolChains.add(new UnavailableToolChain(ToolFamily.VISUAL_CPP));
        }

        toolChains.sort(LATEST_RELEASED_FIRST);

        return toolChains;
    }

    static private ToolChainCandidate findMinGW() {
        // Search in the standard installation locations
        File compiler64Exe = new File("C:/mingw64/bin/g++.exe");
        if (compiler64Exe.isFile()) {
            File compiler32Exe = new File("C:/mingw32/bin/g++.exe");
            if (compiler32Exe.isFile()) {
                return new InstalledMingwGcc(VersionNumber.UNKNOWN).inPath(compiler64Exe.getParentFile(), compiler32Exe.getParentFile());
            }
        }

        return new UnavailableToolChain(ToolFamily.MINGW_GCC);
    }

    static private ToolChainCandidate findCygwin() {
        // Search in the standard installation locations and construct
        File compiler64Exe = new File("C:/cygwin64/bin/g++.exe");
        if (compiler64Exe.isFile()) {
            File compiler32Exe = new File("C:/cygwin64/bin/i686-pc-cygwin-gcc.exe");
            if (compiler32Exe.isFile()) {
                File cygwin32RuntimePath = new File(compiler32Exe.getParentFile().getParentFile(), "usr/i686-pc-cygwin/sys-root/usr/bin");
                return new InstalledCygwinGcc(VersionNumber.UNKNOWN).inPath(compiler64Exe.getParentFile(), cygwin32RuntimePath);
            } else {
                return new UnavailableToolChain(ToolFamily.CYGWIN_GCC);
            }
        }

        return new UnavailableToolChain(ToolFamily.CYGWIN_GCC);
    }

    static private List<ToolChainCandidate> findGccs(boolean mustFind) {
        GccMetadataProvider versionDeterminer = GccMetadataProvider.forGcc(TestFiles.execActionFactory());

        Set<File> gppCandidates = ImmutableSet.copyOf(OperatingSystem.current().findAllInPath("g++"));
        List<ToolChainCandidate> toolChains = Lists.newArrayList();
        if (!gppCandidates.isEmpty()) {
            File firstInPath = gppCandidates.iterator().next();
            for (File candidate : gppCandidates) {
                SearchResult<GccMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(candidate));
                if (version.isAvailable()) {
                    InstalledGcc gcc = new InstalledGcc(ToolFamily.GCC, version.getComponent().getVersion());
                    if (!candidate.equals(firstInPath)) {
                        // Not the first g++ in the path, needs the path variable updated
                        gcc.inPath(candidate.getParentFile());
                    }
                    toolChains.add(gcc);
                }
            }
        }

        if (mustFind && toolChains.isEmpty()) {
            toolChains.add(new UnavailableToolChain(ToolFamily.GCC));
        }

        toolChains.sort(LATEST_RELEASED_FIRST);

        return toolChains;
    }

    static List<ToolChainCandidate> findSwiftcs() {
        List<ToolChainCandidate> toolChains = Lists.newArrayList();

        SwiftcMetadataProvider versionDeterminer = new SwiftcMetadataProvider(TestFiles.execActionFactory());

        // On Linux, we assume swift is installed into /opt/swift
        File rootSwiftInstall = new File("/opt/swift");
        File[] swiftCandidates = GUtil.getOrDefault(rootSwiftInstall.listFiles(swiftInstall -> swiftInstall.isDirectory() && !swiftInstall.getName().equals("latest")), () -> new File[0]);

        for (File swiftInstall : swiftCandidates) {
            File swiftc = new File(swiftInstall, "/usr/bin/swiftc");
            SearchResult<SwiftcMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(swiftc));
            if (version.isAvailable()) {
                File binDir = swiftc.getParentFile();
                toolChains.add(new InstalledSwiftc(binDir, version.getComponent().getVersion()).inPath(binDir, new File("/usr/bin")));
            }
        }

        // On macOS, we assume co-located Xcode is installed into /opt/xcode and default location at /Applications/Xcode.app
        toolChains.addAll(findXcodes().stream().map(InstalledXcode::getSwiftc).filter(Optional::isPresent).map(Optional::get).collect(Collectors.toList()));

        List<File> swiftcCandidates = OperatingSystem.current().findAllInPath("swiftc");
        for (File candidate : swiftcCandidates) {
            SearchResult<SwiftcMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(candidate));
            if (version.isAvailable()) {
                File binDir = candidate.getParentFile();
                InstalledSwiftc swiftc = new InstalledSwiftc(binDir, version.getComponent().getVersion());
                swiftc.inPath(binDir, new File("/usr/bin"));
                toolChains.add(swiftc);
            }
        }

        if (toolChains.isEmpty()) {
            toolChains.add(new UnavailableToolChain(ToolFamily.SWIFTC));
        } else {
            toolChains.sort(LATEST_RELEASED_FIRST);
        }

        return toolChains;
    }

    public enum ToolFamily {
        GCC("gcc"),
        CLANG("clang"),
        VISUAL_CPP("visual c++"),
        MINGW_GCC("mingw"),
        CYGWIN_GCC("gcc cygwin"),
        SWIFTC("swiftc");

        private final String displayName;

        ToolFamily(String displayName) {
            this.displayName = displayName;
        }
    }

    public static abstract class ToolChainCandidate implements VersionedTool {
        @Override
        public String toString() {
            return getDisplayName();
        }

        public abstract String getDisplayName();

        public abstract ToolFamily getFamily();

        public abstract VersionNumber getVersion();

        public abstract boolean isAvailable();

        public abstract boolean meets(ToolChainRequirement requirement);

        public abstract void initialiseEnvironment();

        public abstract void resetEnvironment();
    }

    public abstract static class InstalledToolChain extends ToolChainCandidate {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServicesTestFixture.getInstance().get(ProcessEnvironment.class);
        protected final List<File> pathEntries = new ArrayList<File>();
        private final ToolFamily family;
        private final VersionNumber version;
        protected final String pathVarName;
        private final String objectFileNameSuffix;

        private String originalPath;

        public InstalledToolChain(ToolFamily family, VersionNumber version) {
            this.family = family;
            this.version = version;
            this.pathVarName = OperatingSystem.current().getPathVar();
            this.objectFileNameSuffix = OperatingSystem.current().isWindows() ? ".obj" : ".o";
        }

        InstalledToolChain inPath(File... pathEntries) {
            Collections.addAll(this.pathEntries, pathEntries);
            return this;
        }

        @Override
        public String getDisplayName() {
            return family.displayName + (version == VersionNumber.UNKNOWN ? "" : " " + version.toString());
        }

        @Override
        public ToolFamily getFamily() {
            return family;
        }

        @Override
        public VersionNumber getVersion() {
            return version;
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
        @Override
        public void initialiseEnvironment() {
            String compilerPath = Joiner.on(File.pathSeparator).join(pathEntries);

            if (compilerPath.length() > 0) {
                originalPath = System.getenv(pathVarName);
                String path = compilerPath + File.pathSeparator + originalPath;
                System.out.println(String.format("Using path %s", path));
                PROCESS_ENVIRONMENT.setEnvironmentVariable(pathVarName, path);
            }
        }

        @Override
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

        protected List<String> toRuntimeEnv() {
            if (pathEntries.isEmpty()) {
                return Collections.emptyList();
            }

            String path = Joiner.on(File.pathSeparator).join(pathEntries) + File.pathSeparator + System.getenv(pathVarName);
            return Collections.singletonList(pathVarName + "=" + path);
        }

        public String getId() {
            return getDisplayName().replaceAll("\\W", "");
        }

        public abstract String getUnitTestPlatform();

        @Override
        public boolean matches(String criteria) {
            // Implement this if you need to specify individual toolchains via "org.gradle.integtest.versions"
            throw new UnsupportedOperationException();
        }

        public String platformSpecificToolChainConfiguration() {
            return "";
        }

        public void configureExecuter(GradleExecuter executer) {
            // Toolchains should be using default configuration
        }
    }

    public static abstract class GccCompatibleToolChain extends InstalledToolChain {
        protected GccCompatibleToolChain(ToolFamily family, VersionNumber version) {
            super(family, version);
        }

        protected File find(String tool) {
            if (getPathEntries().isEmpty()) {
                return OperatingSystem.current().findInPath(tool);
            }
            return new File(getPathEntries().get(0), tool);
        }

        public File getLinker() {
            return getCCompiler();
        }

        public File getStaticLibArchiver() {
            return find("ar");
        }

        public abstract File getCppCompiler();

        public abstract File getCCompiler();

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
        public InstalledGcc(ToolFamily family, VersionNumber version) {
            super(family, version);
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return requirement == ToolChainRequirement.GCC || requirement == ToolChainRequirement.GCC_COMPATIBLE || requirement == ToolChainRequirement.AVAILABLE || requirement == ToolChainRequirement.SUPPORTS_32 || requirement == ToolChainRequirement.SUPPORTS_32_AND_64;
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')\n", getId(), pathEntry.toURI());
            }
            return config;
        }

        @Override
        public File getCppCompiler() {
            return find("g++");
        }

        @Override
        public File getCCompiler() {
            return find("gcc");
        }

        @Override
        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (GNU GCC)", getId());
        }

        @Override
        public String getImplementationClass() {
            return Gcc.class.getSimpleName();
        }

        @Override
        public String getPluginClass() {
            return GccCompilerPlugin.class.getSimpleName();
        }

        @Override
        public String getId() {
            return "gcc";
        }
    }

    public static class InstalledWindowsGcc extends InstalledGcc {
        public InstalledWindowsGcc(ToolFamily family, VersionNumber version) {
            super(family, version);
        }

        /**
         * The environment required to execute a binary created by this toolchain.
         */
        @Override
        public List<String> getRuntimeEnv() {
            return toRuntimeEnv();
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s) {\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("     path file('%s')\n", pathEntry.toURI());
            }
            config += platformSpecificToolChainConfiguration();
            config += "}\n";
            return config;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            switch (requirement) {
                case SUPPORTS_32:
                case WINDOWS_GCC:
                case SUPPORTS_32_AND_64:
                    return true;
                default:
                    return super.meets(requirement);
            }
        }

        @Override
        public String getId() {
            return getDisplayName().replaceAll("\\W", "");
        }
    }

    public static class InstalledCygwinGcc extends InstalledWindowsGcc {
        public InstalledCygwinGcc(VersionNumber version) {
            super(ToolFamily.CYGWIN_GCC, version);
        }

        @Override
        public String platformSpecificToolChainConfiguration() {
            String config = "     eachPlatform { platformToolChain ->\n";
            config += "         if (platformToolChain.platform.architecture.isI386() || platformToolChain.platform.architecture.isArm()) {\n";
            config += "             platformToolChain.cCompiler.executable='i686-pc-cygwin-gcc.exe'\n";
            config += "             platformToolChain.cppCompiler.executable='i686-pc-cygwin-g++.exe'\n";
            config += "             platformToolChain.linker.executable='i686-pc-cygwin-g++.exe'\n";
            config += "             platformToolChain.assembler.executable='i686-pc-cygwin-gcc.exe'\n";
            config += "             platformToolChain.staticLibArchiver.executable='i686-pc-cygwin-ar.exe'\n";
            config += "         }\n";
            config += "     }\n";
            return config;
        }

        @Override
        public String getUnitTestPlatform() {
            return "cygwin";
        }
    }

    public static class InstalledMingwGcc extends InstalledWindowsGcc {
        public InstalledMingwGcc(VersionNumber version) {
            super(ToolFamily.MINGW_GCC, version);
        }

        @Override
        public String platformSpecificToolChainConfiguration() {
            String config = "     eachPlatform { platformToolChain ->\n";
            config += "         if (platformToolChain.platform.architecture.isI386() || platformToolChain.platform.architecture.isArm()) {\n";
            config += "             platformToolChain.cCompiler.executable='i686-w64-mingw32-gcc.exe'\n";
            config += "             platformToolChain.cppCompiler.executable='i686-w64-mingw32-g++.exe'\n";
            config += "             platformToolChain.linker.executable='i686-w64-mingw32-g++.exe'\n";
            config += "             platformToolChain.assembler.executable='i686-w64-mingw32-gcc.exe'\n";
            config += "             platformToolChain.staticLibArchiver.executable='i686-w64-mingw32-gcc-ar.exe'\n";
            config += "         }\n";
            config += "     }\n";
            return config;
        }

        @Override
        public String getUnitTestPlatform() {
            return "mingw";
        }
    }

    public static class InstalledSwiftc extends InstalledToolChain {
        private final File binDir;
        private final VersionNumber compilerVersion;

        public InstalledSwiftc(File binDir, VersionNumber compilerVersion) {
            super(ToolFamily.SWIFTC, compilerVersion);
            this.binDir = binDir;
            this.compilerVersion = compilerVersion;
        }

        public File tool(String name) {
            return new File(binDir, name);
        }

        /**
         * The environment required to execute a binary created by this toolchain.
         */
        @Override
        public List<String> getRuntimeEnv() {
            return toRuntimeEnv();
        }

        @Override
        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (Swiftc)", getId());
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')\n", getId(), pathEntry.toURI());
            }
            return config;
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
            switch (requirement) {
                case SWIFTC:
                    return true;
                case SWIFTC_3:
                    return getVersion().getMajor() == 3;
                case SWIFTC_4:
                    return getVersion().getMajor() == 4;
                case SWIFTC_5:
                    return getVersion().getMajor() == 5;
                case SWIFTC_4_OR_OLDER:
                    return getVersion().getMajor() < 5;
                default:
                    return false;
            }
        }
    }

    static List<InstalledXcode> findXcodes() {
        List<InstalledXcode> xcodes = new ArrayList<>();

        // On macOS, we assume co-located Xcode is installed into /opt/xcode
        File rootXcodeInstall = new File("/opt/xcode");
        List<File> xcodeCandidates = Lists.newArrayList(Arrays.asList(GUtil.getOrDefault(rootXcodeInstall.listFiles(File::isDirectory), () -> new File[0])));
        xcodeCandidates.add(new File("/Applications/Xcode.app")); // Default Xcode installation
        xcodeCandidates.stream().filter(File::exists).forEach(xcodeInstall -> {
            TestFile xcodebuild = new TestFile("/usr/bin/xcodebuild");

            try {
                String output = xcodebuild.execute(Collections.singletonList("-version"), Collections.singletonList("DEVELOPER_DIR=" + xcodeInstall.getAbsolutePath())).getOut();
                Pattern versionRegex = Pattern.compile("Xcode (\\d+\\.\\d+(\\.\\d+)?)");
                Matcher matcher = versionRegex.matcher(output);
                if (matcher.find()) {
                    VersionNumber version = VersionNumber.parse(matcher.group(1));
                    xcodes.add(new InstalledXcode(xcodeInstall, version));
                }
            } catch (RuntimeException re) {
                String msg = String.format("Unable to invoke xcodebuild -version for %s%nCause: %s", xcodeInstall.getAbsolutePath(), re.getCause());
                System.out.println(msg);
            }
        });

        return xcodes;
    }

    public static class InstalledXcode {
        private static final ProcessEnvironment PROCESS_ENVIRONMENT = NativeServicesTestFixture.getInstance().get(ProcessEnvironment.class);
        private final File xcodeDir;
        private final VersionNumber version;
        private String originalDeveloperDir;

        public InstalledXcode(File xcodeDir, VersionNumber version) {
            this.xcodeDir = xcodeDir;
            this.version = version;
        }

        private List<String> getRuntimeEnv() {
            return ImmutableList.of("DEVELOPER_DIR=" + xcodeDir.getAbsolutePath());
        }

        private void initialiseEnvironment() {
            originalDeveloperDir = System.getenv("DEVELOPER_DIR");
            System.out.println(String.format("Using DEVELOPER_DIR %s", xcodeDir.getAbsolutePath()));
            PROCESS_ENVIRONMENT.setEnvironmentVariable("DEVELOPER_DIR", xcodeDir.getAbsolutePath());
        }

        private void resetEnvironment() {
            if (originalDeveloperDir != null) {
                PROCESS_ENVIRONMENT.setEnvironmentVariable("DEVELOPER_DIR", xcodeDir.getAbsolutePath());
            }
        }

        private void configureExecuter(GradleExecuter executer) {
            executer.withEnvironmentVars(ImmutableMap.of("DEVELOPER_DIR", xcodeDir.getAbsolutePath()));
        }

        public Optional<InstalledToolChain> getSwiftc() {
            SwiftcMetadataProvider versionDeterminer = new SwiftcMetadataProvider(TestFiles.execActionFactory());
            File swiftc = new File("/usr/bin/swiftc");
            SearchResult<SwiftcMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(swiftc).environment("DEVELOPER_DIR", xcodeDir.getAbsolutePath()));
            if (!version.isAvailable()) {
                return Optional.empty();
            }

            return Optional.of(new InstalledSwiftc(new File("/usr/bin"), version.getComponent().getVersion()) {
                @Override
                public List<String> getRuntimeEnv() {
                    List<String> result = new ArrayList<>();
                    result.addAll(super.getRuntimeEnv());
                    result.addAll(InstalledXcode.this.getRuntimeEnv());
                    return result;
                }

                @Override
                public void initialiseEnvironment() {
                    super.initialiseEnvironment();
                    InstalledXcode.this.initialiseEnvironment();
                }

                @Override
                public void resetEnvironment() {
                    InstalledXcode.this.resetEnvironment();
                    super.resetEnvironment();
                }

                @Override
                public void configureExecuter(GradleExecuter executer) {
                    super.configureExecuter(executer);
                    InstalledXcode.this.configureExecuter(executer);
                }
            });
        }

        public Optional<InstalledToolChain> getClang() {
            GccMetadataProvider versionDeterminer = GccMetadataProvider.forClang(TestFiles.execActionFactory());
            File clang = new File("/usr/bin/clang");
            SearchResult<GccMetadata> version = versionDeterminer.getCompilerMetaData(Collections.emptyList(), spec -> spec.executable(clang).environment("DEVELOPER_DIR", xcodeDir.getAbsolutePath()));
            if (!version.isAvailable()) {
                return Optional.empty();
            }

            return Optional.of(new InstalledClang(version.getComponent().getVersion()) {
                @Override
                public List<String> getRuntimeEnv() {
                    List<String> result = new ArrayList<>();
                    result.addAll(super.getRuntimeEnv());
                    result.addAll(InstalledXcode.this.getRuntimeEnv());
                    return result;
                }

                @Override
                public void initialiseEnvironment() {
                    super.initialiseEnvironment();
                    InstalledXcode.this.initialiseEnvironment();
                }

                @Override
                public void resetEnvironment() {
                    InstalledXcode.this.resetEnvironment();
                    super.resetEnvironment();
                }

                @Override
                public void configureExecuter(GradleExecuter executer) {
                    super.configureExecuter(executer);
                    InstalledXcode.this.configureExecuter(executer);
                }

                @Override
                public boolean meets(ToolChainRequirement requirement) {
                    if (ToolChainRequirement.SUPPORTS_32.equals(requirement) || ToolChainRequirement.SUPPORTS_32_AND_64.equals(requirement)) {
                        return InstalledXcode.this.version.getMajor() < 10;
                    }
                    return super.meets(requirement);
                }
            });
        }
    }

    public static class InstalledVisualCpp extends InstalledToolChain {
        private final String displayVersion;
        private VersionNumber version;
        private File installDir;
        private File cppCompiler;

        public InstalledVisualCpp(VisualStudioVersion version) {
            super(ToolFamily.VISUAL_CPP, version.getVersion());
            this.displayVersion = version.getYear() + " (" + version.getVersion().toString() + ")";
        }

        @Override
        public String getDisplayName() {
            return getFamily().displayName + " " + displayVersion;
        }

        @Override
        public String getId() {
            return "visualCpp";
        }

        public InstalledVisualCpp withInstall(VisualStudioInstall install) {
            DefaultNativePlatform targetPlatform = new DefaultNativePlatform("default");
            installDir = install.getVisualStudioDir();
            version = install.getVersion();
            org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCpp visualCpp = install.getVisualCpp().forPlatform(targetPlatform);
            cppCompiler = visualCpp.getCompilerExecutable();
            pathEntries.addAll(visualCpp.getPath());
            return this;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            switch (requirement) {
                case AVAILABLE:
                case VISUALCPP:
                case SUPPORTS_32:
                case SUPPORTS_32_AND_64:
                    return true;
                case VISUALCPP_2012_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2012.getVersion()) >= 0;
                case VISUALCPP_2013:
                    return version.equals(VISUALSTUDIO_2013.getVersion());
                case VISUALCPP_2013_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2013.getVersion()) >= 0;
                case VISUALCPP_2015:
                    return version.equals(VISUALSTUDIO_2015.getVersion());
                case VISUALCPP_2015_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2015.getVersion()) >= 0;
                case VISUALCPP_2017:
                    return version.equals(VISUALSTUDIO_2017.getVersion());
                case VISUALCPP_2017_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2017.getVersion()) >= 0;
                case VISUALCPP_2019:
                    return version.equals(VISUALSTUDIO_2019.getVersion());
                case VISUALCPP_2019_OR_NEWER:
                    return version.compareTo(VISUALSTUDIO_2019.getVersion()) >= 0;
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

        @Override
        public String getImplementationClass() {
            return VisualCpp.class.getSimpleName();
        }

        @Override
        public String getInstanceDisplayName() {
            return String.format("Tool chain '%s' (Visual Studio)", getId());
        }

        @Override
        public String getPluginClass() {
            return MicrosoftVisualCppCompilerPlugin.class.getSimpleName();
        }

        @Override
        public boolean isVisualCpp() {
            return true;
        }

        @Override
        public VersionNumber getVersion() {
            return version;
        }

        public File getCppCompiler() {
            return cppCompiler;
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
        public InstalledClang(VersionNumber versionNumber) {
            super(ToolFamily.CLANG, versionNumber);
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            switch (requirement) {
                case AVAILABLE:
                case CLANG:
                case GCC_COMPATIBLE:
                    return true;
                case SUPPORTS_32:
                case SUPPORTS_32_AND_64:
                    return (!OperatingSystem.current().isMacOsX()) || getVersion().compareTo(VersionNumber.parse("10.0.0")) < 0;
                default:
                    return false;
            }
        }

        @Override
        public String getBuildScriptConfig() {
            String config = String.format("%s(%s)\n", getId(), getImplementationClass());
            for (File pathEntry : getPathEntries()) {
                config += String.format("%s.path file('%s')\n", getId(), pathEntry.toURI());
            }
            return config;
        }

        @Override
        public File getCppCompiler() {
            return find("clang++");
        }

        @Override
        public File getCCompiler() {
            return find("clang");
        }

        @Override
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
        private final ToolFamily family;

        public UnavailableToolChain(ToolFamily family) {
            this.family = family;
        }

        @Override
        public boolean meets(ToolChainRequirement requirement) {
            return false;
        }

        @Override
        public String getDisplayName() {
            return family.displayName;
        }

        @Override
        public ToolFamily getFamily() {
            return family;
        }

        @Override
        public VersionNumber getVersion() {
            return VersionNumber.UNKNOWN;
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

        @Override
        public boolean matches(String criteria) {
            return false;
        }
    }
}
