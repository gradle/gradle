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

package org.gradle.nativeplatform.toolchain.internal.msvcpp;

import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolConfigurationInternal;
import org.gradle.nativeplatform.toolchain.internal.tools.DefaultCommandLineToolConfiguration;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class VisualCppToolChain extends ExtendableToolChain<VisualCppPlatformToolChain> implements VisualCpp, NativeToolChainInternal {

    private final String name;
    private final OperatingSystem operatingSystem;
    private final FileResolver fileResolver;

    protected static final Logger LOGGER = LoggerFactory.getLogger(VisualCppToolChain.class);

    public static final String DEFAULT_NAME = "visualCpp";

    private final ExecActionFactory execActionFactory;
    private final VisualStudioLocator visualStudioLocator;
    private final WindowsSdkLocator windowsSdkLocator;
    private final Instantiator instantiator;
    private File installDir;
    private File windowsSdkDir;
    private VisualCppInstall visualCpp;
    private WindowsSdk windowsSdk;
    private ToolChainAvailability availability;

    public VisualCppToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory,
                              VisualStudioLocator visualStudioLocator, WindowsSdkLocator windowsSdkLocator, Instantiator instantiator) {
        super(name, operatingSystem, fileResolver);

        this.name = name;
        this.operatingSystem = operatingSystem;
        this.fileResolver = fileResolver;
        this.execActionFactory = execActionFactory;
        this.visualStudioLocator = visualStudioLocator;
        this.windowsSdkLocator = windowsSdkLocator;
        this.instantiator = instantiator;
    }

    protected String getTypeName() {
        return "Visual Studio";
    }

    public File getInstallDir() {
        return installDir;
    }

    public void setInstallDir(Object installDirPath) {
        this.installDir = resolve(installDirPath);
    }

    public File getWindowsSdkDir() {
        return windowsSdkDir;
    }

    public void setWindowsSdkDir(Object windowsSdkDirPath) {
        this.windowsSdkDir = resolve(windowsSdkDirPath);
    }

    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (visualCpp != null && !visualCpp.isSupportedPlatform(targetPlatform)) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        DefaultVisualCppPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultVisualCppPlatformToolChain.class, targetPlatform, instantiator);
        configureActions.execute(configurableToolChain);

        return new VisualCppPlatformToolProvider(targetPlatform.getOperatingSystem(), configurableToolChain.tools, visualCpp, windowsSdk, targetPlatform, execActionFactory);
    }

    private ToolChainAvailability getAvailability() {
        if (availability == null) {
            availability = new ToolChainAvailability();
            checkAvailable(availability);
        }
        return availability;
    }

    private void checkAvailable(ToolChainAvailability availability) {
        if (!operatingSystem.isWindows()) {
            availability.unavailable("Visual Studio is not available on this operating system.");
            return;
        }
        VisualStudioLocator.SearchResult visualStudioSearchResult = visualStudioLocator.locateVisualStudioInstalls(installDir);
        availability.mustBeAvailable(visualStudioSearchResult);
        if (visualStudioSearchResult.isAvailable()) {
            visualCpp = visualStudioSearchResult.getVisualStudio().getVisualCpp();
        }
        WindowsSdkLocator.SearchResult windowsSdkSearchResult = windowsSdkLocator.locateWindowsSdks(windowsSdkDir);
        availability.mustBeAvailable(windowsSdkSearchResult);
        if (windowsSdkSearchResult.isAvailable()) {
            windowsSdk = windowsSdkSearchResult.getSdk();
        }
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return String.format("Tool chain '%s' (%s)", getName(), getTypeName());
    }

    public static class DefaultVisualCppPlatformToolChain implements VisualCppPlatformToolChain {
        private final NativePlatform platform;
        private final Map<ToolType, CommandLineToolConfigurationInternal> tools;

        public DefaultVisualCppPlatformToolChain(NativePlatform platform, Instantiator instantiator) {
            this.platform = platform;
            tools = new HashMap<ToolType, CommandLineToolConfigurationInternal>();
            tools.put(ToolType.C_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.C_COMPILER));
            tools.put(ToolType.CPP_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.CPP_COMPILER));
            tools.put(ToolType.LINKER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.LINKER));
            tools.put(ToolType.STATIC_LIB_ARCHIVER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.STATIC_LIB_ARCHIVER));
            tools.put(ToolType.ASSEMBLER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.ASSEMBLER));
            tools.put(ToolType.WINDOW_RESOURCES_COMPILER, instantiator.newInstance(DefaultCommandLineToolConfiguration.class, ToolType.WINDOW_RESOURCES_COMPILER));
        }

        public CommandLineToolConfiguration getcCompiler() {
            return tools.get(ToolType.C_COMPILER);
        }

        public CommandLineToolConfiguration getCppCompiler() {
            return tools.get(ToolType.CPP_COMPILER);
        }

        public CommandLineToolConfiguration getRcCompiler() {
            return tools.get(ToolType.WINDOW_RESOURCES_COMPILER);
        }

        public CommandLineToolConfiguration getAssembler() {
            return tools.get(ToolType.ASSEMBLER);
        }

        public CommandLineToolConfiguration getLinker() {
            return tools.get(ToolType.LINKER);
        }

        public CommandLineToolConfiguration getStaticLibArchiver() {
            return tools.get(ToolType.STATIC_LIB_ARCHIVER);
        }

        public NativePlatform getPlatform() {
            return platform;
        }
    }

}
