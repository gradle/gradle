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
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadata;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetadata.Compatibility;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator;
import org.gradle.util.CollectionUtils;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.ArchitectureDescriptorBuilder.*;

public class DefaultVisualStudioLocator implements VisualStudioLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisualStudioLocator.class);
    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_BIN = "bin/";
    private static final String LEGACY_COMPILER_FILENAME = "cl.exe";
    private static final String VS2017_COMPILER_FILENAME = "HostX86/x86/cl.exe";

    private final Map<File, VisualStudioInstall> foundInstalls = new HashMap<File, VisualStudioInstall>();
    private final VisualStudioVersionLocator commandLineLocator;
    private final VisualStudioVersionLocator windowsRegistryLocator;
    private final VisualStudioVersionLocator systemPathLocator;
    private final VisualStudioMetaDataProvider versionDeterminer;
    private final SystemInfo systemInfo;
    private boolean initialised;

    public DefaultVisualStudioLocator(VisualStudioVersionLocator commandLineLocator, VisualStudioVersionLocator windowsRegistryLocator, VisualStudioVersionLocator systemPathLocator, VisualStudioMetaDataProvider versionDeterminer, SystemInfo systemInfo) {
        this.commandLineLocator = commandLineLocator;
        this.windowsRegistryLocator = windowsRegistryLocator;
        this.systemPathLocator = systemPathLocator;
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

            if (foundInstalls.isEmpty()) {
                locateInstallsWith(windowsRegistryLocator);
            }

            if (foundInstalls.isEmpty()) {
                locateInstallsWith(systemPathLocator);
            }

            initialised = true;
        }
    }

    private void locateInstallsWith(VisualStudioVersionLocator versionLocator) {
        List<VisualStudioMetadata> installs = versionLocator.getVisualStudioInstalls();

        for (VisualStudioMetadata install : installs) {
            addInstallIfValid(install, versionLocator.getSource());
        }
    }

    private boolean addInstallIfValid(VisualStudioMetadata install, String source) {
        File visualCppDir = install.getVisualCppDir();
        File visualStudioDir = install.getInstallDir();

        if (foundInstalls.containsKey(visualStudioDir)) {
            return true;
        }

        if (isValidInstall(install) && install.getVisualCppVersion() != VersionNumber.UNKNOWN) {
            LOGGER.debug("Found Visual C++ {} at {}", install.getVisualCppVersion(), visualCppDir);
            VersionNumber visualStudioVersion = install.getVersion();
            String visualStudioDisplayVersion = install.getVersion() == VersionNumber.UNKNOWN ? "from " + source : install.getVersion().toString();
            VisualCppInstall visualCpp = buildVisualCppInstall("Visual C++ " + install.getVisualCppVersion(), visualStudioDir, visualCppDir, install.getVisualCppVersion(), install.getCompatibility());
            VisualStudioInstall visualStudio = new VisualStudioInstall("Visual Studio " + visualStudioDisplayVersion, visualStudioDir, visualStudioVersion, visualCpp);
            foundInstalls.put(visualStudioDir, visualStudio);
            return true;
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ directory {} as it does not look like a Visual C++ installation.", visualCppDir);
            return false;
        }
    }

    private SearchResult locateUserSpecifiedInstall(File candidate) {
        VisualStudioMetadata install = versionDeterminer.getVisualStudioMetadataFromInstallDir(candidate);

        if (install != null && addInstallIfValid(install, "user provided path")) {
            return new InstallFound(foundInstalls.get(install.getInstallDir()));
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", candidate);
            return new InstallNotFound(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate));
        }
    }

    private VisualCppInstall buildVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version, Compatibility compatibility) {
        switch(compatibility) {
            case LEGACY:
                return buildLegacyVisualCppInstall(name, vsPath, basePath, version);
            case VS2017_OR_LATER:
                return buildVisualCppInstall(name, vsPath, basePath, version);
            default:
                throw new IllegalArgumentException("Cannot build VisualCpp install for unknown compatibility level: " + compatibility);
        }
    }

    private VisualCppInstall buildLegacyVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version) {

        List<ArchitectureDescriptorBuilder> architectureDescriptorBuilders = Lists.newArrayList();

        architectureDescriptorBuilders.add(LEGACY_X86_ON_X86);
        architectureDescriptorBuilders.add(LEGACY_AMD64_ON_X86);
        architectureDescriptorBuilders.add(LEGACY_IA64_ON_X86);
        architectureDescriptorBuilders.add(LEGACY_ARM_ON_X86);

        boolean isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64;
        if (isNativeAmd64) {
            // Prefer 64-bit tools when building on a 64-bit OS
            architectureDescriptorBuilders.add(LEGACY_AMD64_ON_AMD64);
            architectureDescriptorBuilders.add(LEGACY_X86_ON_AMD64);
            architectureDescriptorBuilders.add(LEGACY_ARM_ON_AMD64);
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

    private VisualCppInstall buildVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version) {

        List<ArchitectureDescriptorBuilder> architectureDescriptorBuilders = Lists.newArrayList();

        architectureDescriptorBuilders.add(X86_ON_X86);
        architectureDescriptorBuilders.add(AMD64_ON_X86);
        architectureDescriptorBuilders.add(ARM_ON_X86);

        boolean isNativeAmd64 = systemInfo.getArchitecture() == SystemInfo.Architecture.amd64;
        if (isNativeAmd64) {
            // Prefer 64-bit tools when building on a 64-bit OS
            architectureDescriptorBuilders.add(AMD64_ON_AMD64);
            architectureDescriptorBuilders.add(X86_ON_AMD64);
            architectureDescriptorBuilders.add(ARM_ON_AMD64);
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

        return candidate == null ? new InstallNotFound("Could not locate a Visual Studio installation, using the command line tool, Windows registry or system path.") : new InstallFound(candidate);
    }

    private static boolean isValidInstall(VisualStudioMetadata install) {
        switch(install.getCompatibility()) {
            case LEGACY:
                return new File(install.getInstallDir(), PATH_COMMON).isDirectory()
                    && isLegacyVisualCpp(install.getVisualCppDir());
            case VS2017_OR_LATER:
                return new File(install.getInstallDir(), PATH_COMMON).isDirectory()
                    && isVS2017VisualCpp(install.getVisualCppDir());
            default:
                throw new IllegalArgumentException("Cannot determine valid install for unknown compatibility: " + install.getCompatibility());
        }
    }

    private static boolean isLegacyVisualCpp(File candidate) {
        return new File(candidate, PATH_BIN + LEGACY_COMPILER_FILENAME).isFile();
    }

    private static boolean isVS2017VisualCpp(File candidate) {
        return new File(candidate, PATH_BIN + VS2017_COMPILER_FILENAME).isFile();
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
}
