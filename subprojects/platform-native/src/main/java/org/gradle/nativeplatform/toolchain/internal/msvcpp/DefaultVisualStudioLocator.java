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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.Transformer;
import org.gradle.nativeplatform.platform.Architecture;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioInstallCandidate.Compatibility;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionLocator;
import org.gradle.platform.base.internal.toolchain.ComponentFound;
import org.gradle.platform.base.internal.toolchain.ComponentNotFound;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.util.internal.VersionNumber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.gradle.nativeplatform.toolchain.internal.msvcpp.ArchitectureDescriptorBuilder.*;

public class DefaultVisualStudioLocator implements VisualStudioLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultVisualStudioLocator.class);
    private static final String PATH_COMMON = "Common7/";
    private static final String PATH_BIN = "bin/";
    private static final String LEGACY_COMPILER_FILENAME = "cl.exe";
    private static final String VS2017_COMPILER_FILENAME = "HostX86/x86/cl.exe";

    private final Map<File, VisualStudioInstall> foundInstalls = new HashMap<File, VisualStudioInstall>();
    private final Set<File> brokenInstalls = new LinkedHashSet<File>();
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
    public List<? extends VisualStudioInstall> locateAllComponents() {
        initializeVisualStudioInstalls();

        return CollectionUtils.sort(foundInstalls.values(), new Comparator<VisualStudioInstall>() {
            @Override
            public int compare(VisualStudioInstall o1, VisualStudioInstall o2) {
                return o2.getVersion().compareTo(o1.getVersion());
            }
        });
    }

    @Override
    public SearchResult<VisualStudioInstall> locateComponent(@Nullable File candidate) {
        initializeVisualStudioInstalls();

        if (candidate != null) {
            return locateUserSpecifiedInstall(candidate);
        }

        return determineDefaultInstall();
    }

    private void initializeVisualStudioInstalls() {
        if (!initialised) {
            locateInstallsWith(commandLineLocator);
            locateInstallsWith(windowsRegistryLocator);
            if (foundInstalls.isEmpty()) {
                locateInstallsWith(systemPathLocator);
            }

            initialised = true;
        }
    }

    private void locateInstallsWith(VisualStudioVersionLocator versionLocator) {
        List<VisualStudioInstallCandidate> installs = versionLocator.getVisualStudioInstalls();

        for (VisualStudioInstallCandidate install : installs) {
            addInstallIfValid(install, versionLocator.getSource());
        }
    }

    private boolean addInstallIfValid(VisualStudioInstallCandidate install, String source) {
        File visualCppDir = install.getVisualCppDir();
        File visualStudioDir = install.getInstallDir();

        if (foundInstalls.containsKey(visualStudioDir)) {
            return true;
        }
        if (brokenInstalls.contains(visualStudioDir)) {
            return false;
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
            brokenInstalls.add(visualStudioDir);
            return false;
        }
    }

    private SearchResult<VisualStudioInstall> locateUserSpecifiedInstall(File candidate) {
        VisualStudioInstallCandidate install = versionDeterminer.getVisualStudioMetadataFromInstallDir(candidate);

        if (install != null && addInstallIfValid(install, "user provided path")) {
            return new ComponentFound<VisualStudioInstall>(foundInstalls.get(install.getInstallDir()));
        } else {
            LOGGER.debug("Ignoring candidate Visual C++ install for {} as it does not look like a Visual C++ installation.", candidate);
            return new ComponentNotFound<VisualStudioInstall>(String.format("The specified installation directory '%s' does not appear to contain a Visual Studio installation.", candidate));
        }
    }

    private VisualCppInstall buildVisualCppInstall(String name, File vsPath, File basePath, VersionNumber version, Compatibility compatibility) {
        switch (compatibility) {
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
        Map<Architecture, ArchitectureSpecificVisualCpp> descriptors = Maps.newHashMap();
        for (ArchitectureDescriptorBuilder architectureDescriptorBuilder : architectureDescriptorBuilders) {
            ArchitectureSpecificVisualCpp descriptor = architectureDescriptorBuilder.buildDescriptor(version, basePath, vsPath);
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
        Map<Architecture, ArchitectureSpecificVisualCpp> descriptors = Maps.newHashMap();
        for (ArchitectureDescriptorBuilder architectureDescriptorBuilder : architectureDescriptorBuilders) {
            ArchitectureSpecificVisualCpp descriptor = architectureDescriptorBuilder.buildDescriptor(version, basePath, vsPath);
            if (descriptor.isInstalled()) {
                descriptors.put(architectureDescriptorBuilder.architecture, descriptor);
            }
        }

        return new VisualCppInstall(name, version, descriptors);
    }

    private SearchResult<VisualStudioInstall> determineDefaultInstall() {
        VisualStudioInstall candidate = null;

        for (VisualStudioInstall visualStudio : foundInstalls.values()) {
            if (candidate == null || visualStudio.getVersion().compareTo(candidate.getVersion()) > 0) {
                candidate = visualStudio;
            }
        }

        if (candidate != null) {
            return new ComponentFound<VisualStudioInstall>(candidate);
        }
        if (brokenInstalls.isEmpty()) {
            return new ComponentNotFound<VisualStudioInstall>("Could not locate a Visual Studio installation, using the command line tool, Windows registry or system path.");
        }
        return new ComponentNotFound<VisualStudioInstall>("Could not locate a Visual Studio installation. None of the following locations contain a valid installation",
            CollectionUtils.collect(brokenInstalls, new ArrayList<String>(), new Transformer<String, File>() {
                @Override
                public String transform(File file) {
                    return file.getAbsolutePath();
                }
            })
        );
    }

    private static boolean isValidInstall(VisualStudioInstallCandidate install) {
        switch (install.getCompatibility()) {
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
}
