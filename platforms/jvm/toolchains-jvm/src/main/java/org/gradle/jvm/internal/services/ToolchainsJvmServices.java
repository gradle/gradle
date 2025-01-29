/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.jvm.internal.services;

import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.jvm.Jvm;
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.scopes.AbstractGradleModuleServices;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentJvmToolchainSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainResolverService;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.DefaultJvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.DefaultOsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.DefaultToolchainExternalResourceFactory;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.platform.internal.CurrentBuildPlatform;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;

public class ToolchainsJvmServices extends AbstractGradleModuleServices {

    protected static class GlobalServices implements ServiceRegistrationProvider {
        @Provides
        protected CurrentJvmToolchainSpec createJavaToolchainSpec(ObjectFactory objectFactory, Jvm currentJvm) {
            return objectFactory.newInstance(CurrentJvmToolchainSpec.class);
        }

        public void configure(ServiceRegistration registration) {
            registration.add(CurrentBuildPlatform.class);
            registration.add(JavaToolchainHttpRedirectVerifierFactory.class);
        }
    }

    protected static class BuildServices implements ServiceRegistrationProvider {

        @Provides
        protected JavaToolchainResolverRegistryInternal createJavaToolchainResolverRegistry(
            Gradle gradle,
            Instantiator instantiator,
            ObjectFactory objectFactory,
            ProviderFactory providerFactory,
            AuthenticationSchemeRegistry authenticationSchemeRegistry) {
            return objectFactory.newInstance(DefaultJavaToolchainResolverRegistry.class, gradle, instantiator, objectFactory, providerFactory, authenticationSchemeRegistry);
        }

        @Provides
        protected JvmToolchainManagement createToolchainManagement(ObjectFactory objectFactory, JavaToolchainResolverRegistry registry) {
            return objectFactory.newInstance(DefaultJvmToolchainManagement.class, registry);
        }

        @Provides
        protected JdkCacheDirectory createJdkCacheDirectory(ObjectFactory objectFactory, GradleUserHomeDirProvider homeDirProvider, FileOperations operations, FileLockManager lockManager, ClientExecHandleBuilderFactory execHandleFactory, GradleUserHomeTemporaryFileProvider temporaryFileProvider) {
            DefaultJvmMetadataDetector silentDetector = new DefaultJvmMetadataDetector(execHandleFactory, temporaryFileProvider);
            return new DefaultJdkCacheDirectory(homeDirProvider, operations, lockManager, silentDetector, temporaryFileProvider);
        }

        public void configure(ServiceRegistration registration) {
            registration.add(ToolchainConfiguration.class, ProviderBackedToolchainConfiguration.class);
            registration.add(OsXJavaHomeCommand.class, DefaultOsXJavaHomeCommand.class);

            // NOTE: These need to be kept in sync with DaemonClientToolchainServices
            registration.add(InstallationSupplier.class, AsdfInstallationSupplier.class);
            registration.add(InstallationSupplier.class, IntellijInstallationSupplier.class);
            registration.add(InstallationSupplier.class, JabbaInstallationSupplier.class);
            registration.add(InstallationSupplier.class, SdkmanInstallationSupplier.class);
            registration.add(InstallationSupplier.class, MavenToolchainsInstallationSupplier.class);

            registration.add(InstallationSupplier.class, LinuxInstallationSupplier.class);
            registration.add(InstallationSupplier.class, OsXInstallationSupplier.class);
            registration.add(InstallationSupplier.class, WindowsInstallationSupplier.class);

            registration.add(JavaInstallationRegistry.class, DefaultJavaInstallationRegistry.class);
            // This has a dependency on RepositoryTransportFactory, which is build scoped, and is required by the following services as well
            registration.add(ExternalResourceFactory.class, DefaultToolchainExternalResourceFactory.class);
            registration.add(SecureFileDownloader.class);
            registration.add(JavaToolchainProvisioningService.class, DefaultJavaToolchainProvisioningService.class);
            registration.add(JavaToolchainQueryService.class);

        }
    }

    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
        registration.addProvider(new GlobalServices());
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.add(JvmInstallationProblemReporter.class);
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildServices());
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(DefaultJavaToolchainResolverService.class);
        registration.add(DefaultJavaToolchainService.class);
    }
}
