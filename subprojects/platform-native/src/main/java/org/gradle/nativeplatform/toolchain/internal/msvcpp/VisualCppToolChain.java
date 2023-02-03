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
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.EmptySystemLibraries;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguage;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnsupportedPlatformToolProvider;
import org.gradle.platform.base.internal.toolchain.SearchResult;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VisualCppToolChain extends ExtendableToolChain<VisualCppPlatformToolChain> implements org.gradle.nativeplatform.toolchain.VisualCpp {

    private final String name;
    private final OperatingSystem operatingSystem;

    protected static final Logger LOGGER = LoggerFactory.getLogger(VisualCppToolChain.class);

    public static final String DEFAULT_NAME = "visualCpp";

    private final ExecActionFactory execActionFactory;
    private final VisualStudioLocator visualStudioLocator;
    private final WindowsSdkLocator windowsSdkLocator;
    private final UcrtLocator ucrtLocator;
    private final Instantiator instantiator;
    private final CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory;
    private final WorkerLeaseService workerLeaseService;

    private File installDir;
    private File ucrtDir;
    private File windowsSdkDir;
    private UcrtInstall ucrt;
    private VisualStudioInstall visualStudio;
    private VisualCppInstall visualCpp;
    private WindowsSdkInstall windowsSdk;
    private ToolChainAvailability availability;

    public VisualCppToolChain(String name, BuildOperationExecutor buildOperationExecutor, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory,
                              CompilerOutputFileNamingSchemeFactory compilerOutputFileNamingSchemeFactory, VisualStudioLocator visualStudioLocator, WindowsSdkLocator windowsSdkLocator, UcrtLocator ucrtLocator, Instantiator instantiator, WorkerLeaseService workerLeaseService) {
        super(name, buildOperationExecutor, operatingSystem, fileResolver);
        this.name = name;
        this.operatingSystem = operatingSystem;
        this.execActionFactory = execActionFactory;
        this.compilerOutputFileNamingSchemeFactory = compilerOutputFileNamingSchemeFactory;
        this.visualStudioLocator = visualStudioLocator;
        this.windowsSdkLocator = windowsSdkLocator;
        this.ucrtLocator = ucrtLocator;
        this.instantiator = instantiator;
        this.workerLeaseService = workerLeaseService;
    }

    @Override
    protected String getTypeName() {
        return "Visual Studio";
    }

    @Override
    public File getInstallDir() {
        return installDir;
    }

    @Override
    public void setInstallDir(Object installDirPath) {
        this.installDir = resolve(installDirPath);
    }

    @Override
    public File getWindowsSdkDir() {
        return windowsSdkDir;
    }

    @Override
    public void setWindowsSdkDir(Object windowsSdkDirPath) {
        this.windowsSdkDir = resolve(windowsSdkDirPath);
    }

    public File getUcrtDir() {
        return ucrtDir;
    }

    public void setUcrtDir(Object ucrtDirPath) {
        this.ucrtDir = resolve(ucrtDirPath);
    }

    @Override
    public PlatformToolProvider select(NativePlatformInternal targetPlatform) {
        ToolChainAvailability result = new ToolChainAvailability();
        result.mustBeAvailable(getAvailability());
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        VisualCpp platformVisualCpp = visualCpp == null ? null : visualCpp.forPlatform(targetPlatform);
        if (platformVisualCpp == null) {
            return new UnsupportedPlatformToolProvider(targetPlatform.getOperatingSystem(), String.format("Don't know how to build for %s.", targetPlatform.getDisplayName()));
        }
        WindowsSdk platformSdk = windowsSdk.forPlatform(targetPlatform);
        SystemLibraries cRuntime = ucrt == null ? new EmptySystemLibraries() : ucrt.getCRuntime(targetPlatform);

        DefaultVisualCppPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultVisualCppPlatformToolChain.class, targetPlatform, instantiator);
        configureActions.execute(configurableToolChain);

        return new VisualCppPlatformToolProvider(buildOperationExecutor, targetPlatform.getOperatingSystem(), configurableToolChain.tools, visualStudio, platformVisualCpp, platformSdk, cRuntime, execActionFactory, compilerOutputFileNamingSchemeFactory, workerLeaseService);
    }

    @Override
    public PlatformToolProvider select(NativeLanguage sourceLanguage, NativePlatformInternal targetMachine) {
        switch (sourceLanguage) {
            case CPP:
                PlatformToolProvider toolProvider = select(targetMachine);
                if (!toolProvider.isAvailable()) {
                    return toolProvider;
                }
                ToolSearchResult cppCompiler = toolProvider.locateTool(ToolType.CPP_COMPILER);
                if (!cppCompiler.isAvailable()) {
                    return new UnavailablePlatformToolProvider(targetMachine.getOperatingSystem(), cppCompiler);
                }
                return toolProvider;
            case ANY:
                return select(targetMachine);
            default:
                return new UnsupportedPlatformToolProvider(targetMachine.getOperatingSystem(), String.format("Don't know how to compile language %s.", sourceLanguage));
        }
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

        // TODO - this selection should happen per target platform

        SearchResult<VisualStudioInstall> visualStudioSearchResult = visualStudioLocator.locateComponent(installDir);
        availability.mustBeAvailable(visualStudioSearchResult);
        if (visualStudioSearchResult.isAvailable()) {
            visualStudio = visualStudioSearchResult.getComponent();
            visualCpp = visualStudioSearchResult.getComponent().getVisualCpp();
        }

        SearchResult<WindowsSdkInstall> windowsSdkSearchResult = windowsSdkLocator.locateComponent(windowsSdkDir);
        availability.mustBeAvailable(windowsSdkSearchResult);
        if (windowsSdkSearchResult.isAvailable()) {
            windowsSdk = windowsSdkSearchResult.getComponent();
        }

        // Universal CRT is required only for VS2015
        if (isVisualCpp2015()) {
            SearchResult<UcrtInstall> ucrtSearchResult = ucrtLocator.locateComponent(ucrtDir);
            availability.mustBeAvailable(ucrtSearchResult);
            if (ucrtSearchResult.isAvailable()) {
                ucrt = ucrtSearchResult.getComponent();
            }
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDisplayName() {
        return "Tool chain '" + getName() + "' (" + getTypeName() + ")";
    }

    public boolean isVisualCpp2015() {
        return visualCpp != null && visualCpp.getVersion().getMajor() >= 14;
    }
}
