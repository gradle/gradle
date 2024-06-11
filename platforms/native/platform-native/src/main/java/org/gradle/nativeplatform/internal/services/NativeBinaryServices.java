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

package org.gradle.nativeplatform.internal.services;

import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.internal.file.RelativeFilePathResolver;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.DefaultTargetMachineFactory;
import org.gradle.nativeplatform.internal.NativeBinaryRenderer;
import org.gradle.nativeplatform.internal.NativeExecutableBinaryRenderer;
import org.gradle.nativeplatform.internal.NativePlatformResolver;
import org.gradle.nativeplatform.internal.SharedLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.StaticLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolverServices;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.SystemLibraryDiscovery;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultUcrtLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultWindowsSdkLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.CommandLineToolVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.DefaultVisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.DefaultVswhereVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.SystemPathVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionDeterminer;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VswhereVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.WindowsRegistryVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPathLocator;
import org.gradle.nativeplatform.toolchain.internal.xcode.MacOSSdkPlatformPathLocator;
import org.gradle.nativeplatform.toolchain.internal.xcode.SwiftStdlibToolLocator;
import org.gradle.process.internal.ExecActionFactory;

public class NativeBinaryServices extends AbstractGradleModuleServices {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(NativeBinaryRenderer.class);
        registration.add(SharedLibraryBinaryRenderer.class);
        registration.add(StaticLibraryBinaryRenderer.class);
        registration.add(NativeExecutableBinaryRenderer.class);
        registration.add(NativePlatforms.class);
        registration.add(NativePlatformResolver.class);
        registration.add(DefaultTargetMachineFactory.class);
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
        registration.add(DefaultUcrtLocator.class);
        registration.add(MacOSSdkPathLocator.class);
        registration.add(MacOSSdkPlatformPathLocator.class);
        registration.add(SwiftStdlibToolLocator.class);
        registration.add(SystemLibraryDiscovery.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new NativeDependencyResolverServices());
        registration.add(CompilerMetaDataProviderFactory.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.addProvider(new ProjectCompilerServices());
    }

    private static final class BuildSessionScopeServices implements ServiceRegistrationProvider {
        @Provides
        WindowsSdkLocator createWindowsSdkLocator(OperatingSystem os, WindowsRegistry windowsRegistry, SystemInfo systemInfo) {
            return new DefaultWindowsSdkLocator(os, windowsRegistry, systemInfo);
        }

        @Provides
        VisualCppMetadataProvider createVisualCppMetadataProvider(WindowsRegistry windowsRegistry) {
            return new DefaultVisualCppMetadataProvider(windowsRegistry);
        }

        @Provides
        WindowsRegistryVersionLocator createWindowsRegistryVersionLocator(WindowsRegistry windowsRegistry) {
            return new WindowsRegistryVersionLocator(windowsRegistry);
        }

        @Provides
        CommandLineToolVersionLocator createCommandLineVersionLocator(ExecActionFactory execActionFactory, VisualCppMetadataProvider visualCppMetadataProvider, VswhereVersionLocator vswhereLocator) {
            return new CommandLineToolVersionLocator(execActionFactory, visualCppMetadataProvider, vswhereLocator);
        }

        @Provides
        VswhereVersionLocator createVswhereVersionLocator(WindowsRegistry windowsRegistry, OperatingSystem os) {
            return new DefaultVswhereVersionLocator(windowsRegistry, os);
        }

        @Provides
        SystemPathVersionLocator createSystemPathVersionLocator(OperatingSystem os, VisualStudioMetaDataProvider versionDeterminer) {
            return new SystemPathVersionLocator(os, versionDeterminer);
        }

        @Provides
        VisualStudioMetaDataProvider createVisualStudioMetadataProvider(CommandLineToolVersionLocator commandLineToolVersionLocator, WindowsRegistryVersionLocator windowsRegistryVersionLocator, VisualCppMetadataProvider visualCppMetadataProvider) {
            return new VisualStudioVersionDeterminer(commandLineToolVersionLocator, windowsRegistryVersionLocator, visualCppMetadataProvider);
        }

        @Provides
        VisualStudioLocator createVisualStudioLocator(CommandLineToolVersionLocator commandLineLocator, WindowsRegistryVersionLocator windowsRegistryLocator, SystemPathVersionLocator systemPathLocator, VisualStudioMetaDataProvider versionDeterminer, SystemInfo systemInfo) {
            return new DefaultVisualStudioLocator(commandLineLocator, windowsRegistryLocator, systemPathLocator, versionDeterminer, systemInfo);
        }
    }

    private static final class ProjectCompilerServices implements ServiceRegistrationProvider {
        @Provides
        CompilerOutputFileNamingSchemeFactory createCompilerOutputFileNamingSchemeFactory(RelativeFilePathResolver fileResolver) {
            return new CompilerOutputFileNamingSchemeFactory(fileResolver);
        }
    }

}
