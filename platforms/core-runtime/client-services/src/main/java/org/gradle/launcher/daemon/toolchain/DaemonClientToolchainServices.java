/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.launcher.daemon.toolchain;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.resource.ExternalResourceFactory;
import org.gradle.internal.resource.transport.http.HttpClientHelper;
import org.gradle.internal.time.Clock;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultOsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.platform.internal.CurrentBuildPlatform;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.util.List;
import java.util.Optional;

public class DaemonClientToolchainServices implements ServiceRegistrationProvider {

    private final ToolchainConfiguration toolchainConfiguration;
    private final ToolchainDownloadUrlProvider toolchainDownloadUrlProvider;
    private final Optional<InternalBuildProgressListener> buildProgressListener;

    public DaemonClientToolchainServices(ToolchainConfiguration toolchainConfiguration, ToolchainDownloadUrlProvider toolchainDownloadUrlProvider, Optional<InternalBuildProgressListener> buildProgressListener) {
        this.toolchainConfiguration = toolchainConfiguration;
        this.toolchainDownloadUrlProvider = toolchainDownloadUrlProvider;
        this.buildProgressListener = buildProgressListener;
    }

    public void configure(ServiceRegistration registration) {
        registration.add(ToolchainConfiguration.class, toolchainConfiguration);
        registration.add(OsXJavaHomeCommand.class, DefaultOsXJavaHomeCommand.class);

        // NOTE: These need to be kept in sync with ToolchainsJvmServices
        registration.add(InstallationSupplier.class, AsdfInstallationSupplier.class);
        registration.add(InstallationSupplier.class, IntellijInstallationSupplier.class);
        registration.add(InstallationSupplier.class, JabbaInstallationSupplier.class);
        registration.add(InstallationSupplier.class, SdkmanInstallationSupplier.class);

//        registration.add(InstallationSupplier.class, MavenToolchainsInstallationSupplier.class);

        registration.add(InstallationSupplier.class, LinuxInstallationSupplier.class);
        registration.add(InstallationSupplier.class, OsXInstallationSupplier.class);
        registration.add(InstallationSupplier.class, WindowsInstallationSupplier.class);

        registration.add(CurrentBuildPlatform.class);
    }



    @Provides
    protected DaemonJavaToolchainProvisioningService createDaemonJavaToolchainProvisioningService(SecureFileDownloader secureFileDownloader, JdkCacheDirectory jdkCacheDirectory, CurrentBuildPlatform buildPlatform) {
        return new DaemonJavaToolchainProvisioningService(secureFileDownloader, jdkCacheDirectory, buildPlatform, toolchainDownloadUrlProvider, toolchainConfiguration.isDownloadEnabled());
    }

    @Provides
    protected JavaToolchainQueryService createJavaToolchainQueryService(JvmMetadataDetector jvmMetadataDetector, JavaToolchainProvisioningService javaToolchainProvisioningService, FileFactory fileFactory, JavaInstallationRegistry javaInstallationRegistry) {
        return new JavaToolchainQueryService(jvmMetadataDetector, fileFactory, javaToolchainProvisioningService, javaInstallationRegistry, null);
    }

    @Provides
    protected JavaInstallationRegistry createJavaInstallationRegistry(ToolchainConfiguration toolchainConfiguration, List<InstallationSupplier> installationSuppliers, JvmMetadataDetector jvmMetadataDetector, ProgressLoggerFactory progressLoggerFactory, FileResolver fileResolver, JdkCacheDirectory jdkCacheDirectory) {
        return new DefaultJavaInstallationRegistry(toolchainConfiguration, installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory, fileResolver, jdkCacheDirectory, new JvmInstallationProblemReporter());
    }

    @Provides
    protected JdkCacheDirectory createJdkCacheDirectory(GradleUserHomeDirProvider gradleUserHomeDirProvider, FileLockManager fileLockManager, ClientExecHandleBuilderFactory execHandleFactory, GradleUserHomeTemporaryFileProvider gradleUserHomeTemporaryFileProvider) {
        return new DefaultJdkCacheDirectory(gradleUserHomeDirProvider, null, fileLockManager, new DefaultJvmMetadataDetector(execHandleFactory, gradleUserHomeTemporaryFileProvider), gradleUserHomeTemporaryFileProvider);
    }

    @Provides
    protected SecureFileDownloader createSecureFileDownloader(FileSystem fileSystem, ListenerManager listenerManager, JavaToolchainHttpRedirectVerifierFactory httpRedirectVerifierFactory, HttpClientHelper.Factory httpClientHelperFactory, ProgressLoggerFactory progressLoggerFactory, Clock clock) {
        ExternalResourceFactory externalResourceFactory = new DaemonToolchainExternalResourceFactory(fileSystem, listenerManager, httpRedirectVerifierFactory, httpClientHelperFactory, progressLoggerFactory, clock, buildProgressListener);
        return new SecureFileDownloader(externalResourceFactory);
    }

    @Provides
    protected DefaultFilePropertyFactory createFilePropertyFactory(PropertyHost propertyHost, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory) {
        return new DefaultFilePropertyFactory(propertyHost, fileResolver, fileCollectionFactory);
    }

    @Provides
    protected HttpClientHelper.Factory createHttpClientHelperFactory() {
        return HttpClientHelper.Factory.createFactory(new DocumentationRegistry());
    }

    @Provides
    protected JavaToolchainHttpRedirectVerifierFactory createJavaToolchainHttpRedirectVerifierFactory() {
        return new JavaToolchainHttpRedirectVerifierFactory();
    }
}
