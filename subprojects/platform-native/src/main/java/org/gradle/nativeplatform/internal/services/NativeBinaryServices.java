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
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.internal.NativeBinaryRenderer;
import org.gradle.nativeplatform.internal.NativeExecutableBinaryRenderer;
import org.gradle.nativeplatform.internal.NativePlatformResolver;
import org.gradle.nativeplatform.internal.SharedLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.StaticLibraryBinaryRenderer;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyResolverServices;
import org.gradle.nativeplatform.platform.internal.NativePlatforms;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerMetaDataProviderFactory;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultUcrtLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultVisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.DefaultWindowsSdkLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualStudioLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.WindowsSdkLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.CommandLineToolVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.DefaultVisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.SystemPathVersionLocator;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualCppMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioMetaDataProvider;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.VisualStudioVersionDeterminer;
import org.gradle.nativeplatform.toolchain.internal.msvcpp.version.WindowsRegistryVersionLocator;
import org.gradle.process.internal.ExecActionFactory;

public class NativeBinaryServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.add(NativeBinaryRenderer.class);
        registration.add(SharedLibraryBinaryRenderer.class);
        registration.add(StaticLibraryBinaryRenderer.class);
        registration.add(NativeExecutableBinaryRenderer.class);
        registration.add(NativePlatforms.class);
        registration.add(NativePlatformResolver.class);
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeServices());
        registration.add(DefaultUcrtLocator.class);
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

    private static final class BuildSessionScopeServices {
        WindowsSdkLocator createWindowsSdkLocator(OperatingSystem os, WindowsRegistry windowsRegistry) {
            return new DefaultWindowsSdkLocator(os, windowsRegistry);
        }

        VisualCppMetadataProvider createVisualCppMetadataProvider(WindowsRegistry windowsRegistry) {
            return new DefaultVisualCppMetadataProvider(windowsRegistry);
        }

        WindowsRegistryVersionLocator createWindowsRegistryVersionLocator(WindowsRegistry windowsRegistry) {
            return new WindowsRegistryVersionLocator(windowsRegistry);
        }

        CommandLineToolVersionLocator createCommandLineVersionLocator(ExecActionFactory execActionFactory, WindowsRegistry windowsRegistry, OperatingSystem os, VisualCppMetadataProvider visualCppMetadataProvider) {
            return new CommandLineToolVersionLocator(execActionFactory, windowsRegistry, os, visualCppMetadataProvider);
        }

        SystemPathVersionLocator createSystemPathVersionLocator(OperatingSystem os, VisualStudioMetaDataProvider versionDeterminer) {
            return new SystemPathVersionLocator(os, versionDeterminer);
        }

        VisualStudioMetaDataProvider createVisualStudioMetadataProvider(CommandLineToolVersionLocator commandLineToolVersionLocator, WindowsRegistryVersionLocator windowsRegistryVersionLocator, VisualCppMetadataProvider visualCppMetadataProvider) {
            return new VisualStudioVersionDeterminer(commandLineToolVersionLocator, windowsRegistryVersionLocator, visualCppMetadataProvider);
        }

        VisualStudioLocator createVisualStudioLocator(CommandLineToolVersionLocator commandLineLocator, WindowsRegistryVersionLocator windowsRegistryLocator, SystemPathVersionLocator systemPathLocator, VisualStudioMetaDataProvider versionDeterminer, SystemInfo systemInfo) {
            return new DefaultVisualStudioLocator(commandLineLocator, windowsRegistryLocator, systemPathLocator, versionDeterminer, systemInfo);
        }
    }

    private static final class ProjectCompilerServices {
        CompilerOutputFileNamingSchemeFactory createCompilerOutputFileNamingSchemeFactory(RelativeFilePathResolver fileResolver) {
            return new CompilerOutputFileNamingSchemeFactory(fileResolver);
        }
    }

}
