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
package org.gradle.nativebinaries.toolchain.internal.gcc;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativebinaries.platform.Platform;
import org.gradle.nativebinaries.platform.internal.ArchitectureInternal;
import org.gradle.nativebinaries.toolchain.PlatformConfigurableToolChain;
import org.gradle.nativebinaries.toolchain.TargetPlatformConfiguration;
import org.gradle.nativebinaries.toolchain.internal.*;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;

/**
 * A tool chain that has GCC semantics, where all platform variants are produced by varying the tool args.
 */
public abstract class AbstractGccCompatibleToolChain extends AbstractToolChain implements PlatformConfigurableToolChain {
    private final ExecActionFactory execActionFactory;
    protected final ToolRegistry tools;

    private final List<TargetPlatformConfiguration> platformConfigs = new ArrayList<TargetPlatformConfiguration>();
    private int configInsertLocation;

    public AbstractGccCompatibleToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolRegistry tools) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.tools = tools;

        addPlatformConfiguration(new ToolChainDefaultArchitecture());
        addPlatformConfiguration(new Intel32Architecture());
        addPlatformConfiguration(new Intel64Architecture());
        configInsertLocation = 0;
    }

    public List<File> getPath() {
        return tools.getPath();
    }

    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            tools.path(resolve(path));
        }
    }

    @Override
    protected void checkAvailable(ToolChainAvailability availability) {
        for (ToolType key : ToolType.values()) {
            availability.mustBeAvailable(tools.locate(key));
        }
    }

    public void addPlatformConfiguration(TargetPlatformConfiguration platformConfig) {
        platformConfigs.add(configInsertLocation, platformConfig);
        configInsertLocation++;
    }

    public PlatformToolChain target(Platform targetPlatform) {
        assertAvailable();
        TargetPlatformConfiguration platformConfiguration = getPlatformConfiguration(targetPlatform);
        if (platformConfiguration == null) {
            throw new IllegalStateException(String.format("Tool chain %s cannot build for platform: %s", getName(), targetPlatform.getName()));
        }
        return new GccPlatformToolChain(tools, execActionFactory, platformConfiguration, canUseCommandFile());
    }

    protected TargetPlatformConfiguration getPlatformConfiguration(Platform targetPlatform) {
        for (TargetPlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
    }

    protected boolean canUseCommandFile() {
        return true;
    }

    public ToolSearchResult canTargetPlatform(final Platform targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (getPlatformConfiguration(targetPlatform) == null) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        return result;
    }

    private static class ToolChainDefaultArchitecture implements TargetPlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                && targetPlatform.getArchitecture() == ArchitectureInternal.TOOL_CHAIN_DEFAULT;
        }

        public List<String> getAssemblerArgs() {
            return emptyList();
        }

        public List<String> getCppCompilerArgs() {
            return emptyList();
        }

        public List<String> getCCompilerArgs() {
            return emptyList();
        }

        public List<String> getObjectiveCppCompilerArgs() {
            return emptyList();
        }

        public List<String> getObjectiveCCompilerArgs() {
            return emptyList();
        }

        public List<String> getStaticLibraryArchiverArgs() {
            return emptyList();
        }

        public List<String> getLinkerArgs() {
            return emptyList();
        }
    }

    private static class Intel32Architecture implements TargetPlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isI386();
        }

        public List<String> getCppCompilerArgs() {
            return asList("-m32");
        }

        public List<String> getCCompilerArgs() {
            return asList("-m32");
        }

        public List<String> getObjectiveCppCompilerArgs() {
            return asList("-m32");
        }

        public List<String> getObjectiveCCompilerArgs() {
            return asList("-m32");
        }

        public List<String> getAssemblerArgs() {
            if (OperatingSystem.current().isMacOsX()) {
                return asList("-arch", "i386");
            } else {
                return asList("--32");
            }
        }

        public List<String> getLinkerArgs() {
            return asList("-m32");
        }

        public List<String> getStaticLibraryArchiverArgs() {
            return emptyList();
        }
    }

    private static class Intel64Architecture implements TargetPlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && !OperatingSystem.current().isWindows() // Currently don't support building 64-bit binaries on GCC/Windows
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isAmd64();
        }
        public List<String> getCppCompilerArgs() {
            return asList("-m64");
        }

        public List<String> getCCompilerArgs() {
            return asList("-m64");
        }

        public List<String> getObjectiveCppCompilerArgs() {
            return asList("-m64");
        }

        public List<String> getObjectiveCCompilerArgs() {
            return asList("-m64");
        }

        public List<String> getAssemblerArgs() {
            if (OperatingSystem.current().isMacOsX()) {
                return asList("-arch", "x86_64");
            } else {
                return asList("--64");
            }
        }

        public List<String> getLinkerArgs() {
            return asList("-m64");
        }

        public List<String> getStaticLibraryArchiverArgs() {
            return emptyList();
        }
    }

}
