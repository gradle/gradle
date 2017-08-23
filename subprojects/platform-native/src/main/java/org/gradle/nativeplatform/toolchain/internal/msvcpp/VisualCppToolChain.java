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
import org.gradle.nativeplatform.toolchain.VisualCpp;
import org.gradle.nativeplatform.toolchain.VisualCppPlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.ExtendableToolChain;
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.platform.base.internal.toolchain.ToolChainAvailability;
import org.gradle.process.internal.ExecActionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class VisualCppToolChain extends ExtendableToolChain<VisualCppPlatformToolChain> implements VisualCpp, NativeToolChainInternal {

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
    private Ucrt ucrt;
    private VisualCppInstall visualCpp;
    private WindowsSdk windowsSdk;
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
        if (visualCpp != null && !visualCpp.isSupportedPlatform(targetPlatform)) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
        }
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(targetPlatform.getOperatingSystem(), result);
        }

        DefaultVisualCppPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultVisualCppPlatformToolChain.class, targetPlatform, instantiator);
        configureActions.execute(configurableToolChain);

        return new VisualCppPlatformToolProvider(buildOperationExecutor, targetPlatform.getOperatingSystem(), configurableToolChain.tools, visualCpp, windowsSdk, ucrt, targetPlatform, execActionFactory, compilerOutputFileNamingSchemeFactory, workerLeaseService);
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
        VisualStudioLocator.SearchResult visualStudioSearchResult = visualStudioLocator.locateDefaultVisualStudioInstall(installDir);
        availability.mustBeAvailable(visualStudioSearchResult);
        if (visualStudioSearchResult.isAvailable()) {
            visualCpp = visualStudioSearchResult.getVisualStudio().getVisualCpp();
        }
        WindowsSdkLocator.SearchResult windowsSdkSearchResult = windowsSdkLocator.locateWindowsSdks(windowsSdkDir);
        availability.mustBeAvailable(windowsSdkSearchResult);
        if (windowsSdkSearchResult.isAvailable()) {
            windowsSdk = windowsSdkSearchResult.getSdk();
        }

        // Universal CRT is required only for VS2015
        if (isVisualCpp2015()) {
            UcrtLocator.SearchResult ucrtSearchResult = ucrtLocator.locateUcrts(ucrtDir);
            availability.mustBeAvailable(ucrtSearchResult);
            if (ucrtSearchResult.isAvailable()) {
                ucrt = ucrtSearchResult.getUcrt();
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
