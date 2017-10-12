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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.Transformer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadata;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

public class DefaultVisualStudioLocator implements VisualStudioLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisualStudioLocator.class);

    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_COMMONTOOLS = PATH_COMMON + "Tools/";
    private static final String PATH_COMMONIDE = PATH_COMMON + "IDE/";
    private static final String PATH_BIN = "bin/";
    private static final String PATH_INCLUDE = "include/";
    private static final String COMPILER_FILENAME = "cl.exe";
    private static final String DEFINE_ARMPARTITIONAVAILABLE = "_ARM_WINAPI_PARTITION_DESKTOP_SDK_AVAILABLE";

    private final Map<File, VisualStudioInstall> foundInstalls = new HashMap<File, VisualStudioInstall>();
    private final OperatingSystem os;
    private final VisualStudioVersionLocator commandLineLocator;
    private final VisualStudioVersionLocator windowsRegistryLocator;
    private final VisualStudioMetaDataProvider versionDeterminer;
    private final SystemInfo systemInfo;
    private VisualStudioInstall pathInstall;
    private boolean initialised;

    public DefaultVisualStudioLocator(OperatingSystem os, VisualStudioVersionLocator commandLineLocator, VisualStudioVersionLocator windowsRegistryLocator, VisualStudioMetaDataProvider versionDeterminer, SystemInfo systemInfo) {
        this.os = os;
        this.commandLineLocator = commandLineLocator;
        this.windowsRegistryLocator = windowsRegistryLocator;
        this.versionDeterminer = versionDeterminer;
        this.systemInfo = systemInfo;
    }

    @Override
    public List<SearchResult> locateAllVisualStudioVersions() {
        initializeVisualStudioInstalls();

        List<VisualStudioInstall> sortedInstalls = CollectionUtils.sort(foundInstalls.values(), new Comparator<VisualStudioInstall>() {
            @Override
            public int compare(VisualStudioInstall o1, VisualStudioInstall o2) {
                return o2.getVersion().compareTo(o1.getVersion());
            }
        });

        if (sortedInstalls.isEmpty()) {
            return Lists.newArrayList((SearchResult)new InstallNotFound("Could not locate a Visual Studio installation, using the Windows registry and system path."));
        } else {
            return CollectionUtils.collect(sortedInstalls, new Transformer<SearchResult, VisualStudioInstall>() {
                @Override
                public SearchResult transform(VisualStudioInstall visualStudioInstall) {
                    return new InstallFound(visualStudioInstall);
                }
            });
        }
    }

    @Override
    public SearchResult locateDefaultVisualStudioInstall() {
        return locateDefaultVisualStudioInstall(null);
    }

    @Override
    public SearchResult locateDefaultVisualStudioInstall(File candidate) {
        if (candidate != null) {
            return locateUserSpecifiedInstall(candidate);
        }

        return determineDefaultInstall();
    }

    private void initializeVisualStudioInstalls() {
        if (!initialised) {
            locateInstallsWith(commandLineLocator);

            if (foundInstalls.size() < 1) {
                locateInstallsWith(windowsRegistryLocator);
            }

            if (foundInstalls.size() < 1) {
                locateInstallInPath();
            }

            initialised = true;
        }
    }

    private void locateInstallsWith(VisualStudioVersionLocator versionLocator) {
        List<VisualStudioMetadata> installs = versionLocator.getVisualStudioInstalls();

        for (VisualStudioMetadata install : installs) {
            addInstallIfValid(install);
        }
    }

    private void addInstallIfValid(VisualStudioMetadata install) {
        File visualCppDir = install.getVisualCppDir();
        File visualStudioDir = install.getInstallDir();

        if (isVisualCpp(visualCppDir) && isVisualStudio(visualStudioDir)) {
            LOGGER.debug("Found Visual C++ {} at {}", install.getVersion(), visualCppDir);
            VersionNumber version = install.getVersion();
            VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ " + install.getVisualCppVersion(), visualStudioDir, visualCppDir, install.getVisualCppVersion());
            VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
            foundInstalls.put(visualStudioDir, visualStudio);
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ directory {} as it does not look like a Visual C++ installation.", visualCppDir);
        }
    }

    private void locateInstallInPath() {
        File compilerInPath = os.findInPath(COMPILER_FILENAME);
        if (compilerInPath == null) {
            LOGGER.debug("No visual c++ compiler found in system path.");
            return;
        }

        VisualStudioMetadata install = versionDeterminer.getVisualStudioMetadataFromCompiler(compilerInPath);

        if (install != null) {
            File visualCppDir = install.getVisualCppDir();
            if (!isVisualCpp(visualCppDir)) {
                visualCppDir = visualCppDir.getParentFile();
                if (!isVisualCpp(visualCppDir)) {
                    LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", compilerInPath);
                    return;
                }
            }
            LOGGER.debug("Found Visual C++ install {} using system path", visualCppDir);

            File visualStudioDir = install.getInstallDir();
            if (!foundInstalls.containsKey(visualStudioDir)) {
                String displayVersion = install.getVersion() == VersionNumber.UNKNOWN ? "from system path" : install.getVersion().toString();
                VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ " + displayVersion, visualStudioDir, visualCppDir, install.getVersion());
                VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
                foundInstalls.put(visualStudioDir, visualStudio);
            }
        }
    }

    private SearchResult locateUserSpecifiedInstall(File candidate) {
        VisualStudioMetadata install = versionDeterminer.getVisualStudioMetadataFromInstallDir(candidate);

        if (install != null) {
            File visualStudioDir = install.getInstallDir();
            File visualCppDir = install.getVisualCppDir();
            if (!isVisualStudio(visualStudioDir) || !isVisualCpp(visualCppDir)) {
                LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", candidate);
                return new InstallNotFound(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate));
            }

            if (!foundInstalls.containsKey(visualStudioDir)) {
                String displayVersion = install.getVersion() == VersionNumber.UNKNOWN ? "from user provided path" : install.getVersion().toString();
                VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ " + displayVersion, visualStudioDir, visualCppDir, install.getVersion());
                VisualStudioInstall visualStudio = new VisualStudioInstall(visualStudioDir, visualCpp);
                foundInstalls.put(visualStudioDir, visualStudio);
            }
            return new InstallFound(foundInstalls.get(visualStudioDir));
        } else {
            return new InstallNotFound(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate));
        }
    }

    private VisualCppInstall buildVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version) {

        List<ArchitectureDescriptorBuilder> architectureDescriptorBuilders = Lists.newArrayList();

        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.X86_ON_X86);
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.AMD64_ON_X86);
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.IA64_ON_X86);
        architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM_ON_X86);

        boolean isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64;
        if (isNativeAmd64) {
            // Prefer 64-bit tools when building on a 64-bit OS
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.AMD64_ON_AMD64);
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.X86_ON_AMD64);
            architectureDescriptorBuilders.add(ArchitectureDescriptorBuilder.ARM_ON_AMD64);
        }

        // populates descriptors, last descriptor in wins for a given architecture
        Map<Architecture, ArchitectureDescriptor> descriptors = Maps.newHashMap();
        for (ArchitectureDescriptorBuilder architectureDescriptorBuilder : architectureDescriptorBuilders) {
            ArchitectureDescriptor descriptor = architectureDescriptorBuilder.buildDescriptor(basePath, vsPath);
            if (descriptor.isInstalled()) {
                descriptors.put(architectureDescriptorBuilder.architecture, descriptor);
            }
        }

        return new VisualCppInstall(name, version, descriptors);
    }

    private SearchResult determineDefaultInstall() {
        initializeVisualStudioInstalls();

        VisualStudioInstall candidate = null;

        for (VisualStudioInstall visualStudio : foundInstalls.values()) {
            if (candidate == null || visualStudio.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = visualStudio;
            }
        }

        return candidate == null ? new InstallNotFound("Could not locate a Visual Studio installation, using the Windows registry and system path.") : new InstallFound(candidate);
    }

    private static boolean isVisualStudio(File candidate) {
        return new File(candidate, PATH_COMMON).isDirectory() && isVisualCpp(new File(candidate, "VC"));
    }

    private static boolean isVisualCpp(File candidate) {
        return new File(candidate, PATH_BIN + COMPILER_FILENAME).isFile();
    }

    private static class InstallFound implements SearchResult {
        private final VisualStudioInstall install;

        public InstallFound(VisualStudioInstall install) {
            this.install = Preconditions.checkNotNull(install);
        }

        @Override
        public VisualStudioInstall getVisualStudio() {
            return install;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    private static class InstallNotFound implements SearchResult {
        private final String message;

        private InstallNotFound(String message) {
            this.message = message;
        }

        @Override
        public VisualStudioInstall getVisualStudio() {
            return null;
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }

    enum ArchitectureDescriptorBuilder {
        AMD64_ON_AMD64("amd64", "bin/amd64", "lib/amd64", "ml64.exe"),
        AMD64_ON_X86("amd64", "bin/x86_amd64", "lib/amd64", "ml64.exe") {
            @Override
            File getCrossCompilePath(File basePath) {
                return X86_ON_X86.getBinPath(basePath);
            }
        },

        X86_ON_AMD64("x86", "bin/amd64_x86", "lib", "ml.exe") {
            @Override
            File getCrossCompilePath(File basePath) {
                return AMD64_ON_AMD64.getBinPath(basePath);
            }
        },
        X86_ON_X86("x86", "bin", "lib", "ml.exe"),

        ARM_ON_AMD64("arm", "bin/amd64_arm", "lib/arm", "armasm.exe") {
            @Override
            File getCrossCompilePath(File basePath) {
                return AMD64_ON_AMD64.getBinPath(basePath);
            }

            @Override
            Map<String, String> getDefinitions() {
                Map<String, String> definitions = super.getDefinitions();
                definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
                return definitions;
            }
        },
        ARM_ON_X86("arm", "bin/x86_arm", "lib/arm", "armasm.exe") {
            @Override
            File getCrossCompilePath(File basePath) {
                return X86_ON_X86.getBinPath(basePath);
            }

            @Override
            Map<String, String> getDefinitions() {
                Map<String, String> definitions = super.getDefinitions();
                definitions.put(DEFINE_ARMPARTITIONAVAILABLE, "1");
                return definitions;
            }
        },

        IA64_ON_X86("ia64", "bin/x86_ia64", "lib/ia64", "ias.exe")  {
            @Override
            File getCrossCompilePath(File basePath) {
                return X86_ON_X86.getBinPath(basePath);
            }
        };

        final Architecture architecture;
        final String binPath;
        final String libPath;
        final String asmFilename;

        ArchitectureDescriptorBuilder(String architecture, String binPath, String libPath, String asmFilename) {
            this.binPath = binPath;
            this.libPath = libPath;
            this.asmFilename = asmFilename;
            this.architecture = Architectures.forInput(architecture);
        }

        File getBinPath(File basePath) {
            return new File(basePath, binPath);
        }

        File getLibPath(File basePath) {
            return new File(basePath, libPath);
        }

        File getCompilerPath(File basePath) {
            return new File(getBinPath(basePath), COMPILER_FILENAME);
        }

        File getCrossCompilePath(File basePath) {
            return null;
        }

        Map<String, String> getDefinitions() {
            return Maps.newHashMap();
        }

        String getAsmFilename() {
            return asmFilename;
        }

        ArchitectureDescriptor buildDescriptor(File basePath, File vsPath) {
            File commonTools = new File(vsPath, PATH_COMMONTOOLS);
            File commonIde = new File(vsPath, PATH_COMMONIDE);
            List<File> paths = Lists.newArrayList(commonTools, commonIde);
            File crossCompilePath = getCrossCompilePath(basePath);
            if (crossCompilePath!=null) {
                paths.add(crossCompilePath);
            }
            File includePath = new File(basePath, PATH_INCLUDE);
            return new DefaultArchitectureDescriptor(paths, getBinPath(basePath), getLibPath(basePath), getCompilerPath(basePath), includePath, asmFilename, getDefinitions());
        }
    }
}
