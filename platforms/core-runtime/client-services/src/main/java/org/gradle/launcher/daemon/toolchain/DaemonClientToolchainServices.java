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

import net.rubygrapefruit.platform.SystemInfo;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.api.internal.file.DefaultFileOperations;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.archive.DecompressionCoordinator;
import org.gradle.api.internal.file.archive.DefaultDecompressionCoordinator;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.api.internal.file.temp.TemporaryFileProvider;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.cache.FileLockManager;
import org.gradle.cache.scopes.ScopedCacheBuilderFactory;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.Deleter;
import org.gradle.internal.file.impl.DefaultDeleter;
import org.gradle.internal.hash.DefaultFileHasher;
import org.gradle.internal.hash.DefaultStreamHasher;
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.DefaultJvmMetadataDetector;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.BuildOperationIdFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.service.Provides;
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
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.JavaToolchainHttpRedirectVerifierFactory;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.platform.internal.CurrentBuildPlatform;
import org.gradle.process.internal.ClientExecHandleBuilderFactory;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Those services are only useful when daemon toolchain is active and there is no compatible running daemon.
 * <p>
 * This is why we only expose one service, wrapped in a {@link Lazy}, and which triggers the creation of all needed instances.
 */
public class DaemonClientToolchainServices implements ServiceRegistrationProvider {

    private final ToolchainConfiguration toolchainConfiguration;
    private final ToolchainDownloadUrlProvider toolchainDownloadUrlProvider;
    private final Optional<InternalBuildProgressListener> buildProgressListener;
    private final BuildLayoutConfiguration buildLayoutConfiguration;

    public DaemonClientToolchainServices(ToolchainConfiguration toolchainConfiguration, ToolchainDownloadUrlProvider toolchainDownloadUrlProvider, BuildLayoutConfiguration buildLayoutConfiguration, Optional<InternalBuildProgressListener> buildProgressListener) {
        this.toolchainConfiguration = toolchainConfiguration;
        this.toolchainDownloadUrlProvider = toolchainDownloadUrlProvider;
        this.buildLayoutConfiguration = buildLayoutConfiguration;
        // TODO passing an optional down is fishy
        this.buildProgressListener = buildProgressListener;
    }

    @Provides
    protected Lazy<JavaToolchainQueryService> createJavaToolchainQueryService(
        JvmMetadataDetector jvmMetadataDetector,
        FileSystem fileSystem,
        ListenerManager listenerManager,
        ProgressLoggerFactory progressLoggerFactory,
        Clock clock,
        BuildOperationIdFactory operationIdFactory,
        GradleUserHomeDirProvider gradleUserHomeDirProvider,
        TemporaryFileProvider temporaryFileProvider,
        FileLockManager fileLockManager,
        ClientExecHandleBuilderFactory execHandleFactory,
        GradleUserHomeTemporaryFileProvider gradleUserHomeTemporaryFileProvider,
        PropertyHost propertyHost,
        FileCollectionFactory fileCollectionFactory,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        PropertyFactory propertyFactory,
        DocumentationRegistry documentationRegistry,
        WindowsRegistry windowsRegistry,
        OperatingSystem os,
        SystemInfo systemInfo,
        ScopedCacheBuilderFactory scopedCacheBuilderFactory,
        BuildLayoutFactory buildLayoutFactory
    ) {
        return Lazy.unsafe().of(() -> {
            // NOTE: These need to be kept in sync with ToolchainsJvmServices
            List<InstallationSupplier> installationSuppliers = new ArrayList<>(8);
            installationSuppliers.add(new AsdfInstallationSupplier(toolchainConfiguration));
            installationSuppliers.add(new IntellijInstallationSupplier(toolchainConfiguration));
            installationSuppliers.add(new JabbaInstallationSupplier(toolchainConfiguration));
            installationSuppliers.add(new SdkmanInstallationSupplier(toolchainConfiguration));
            installationSuppliers.add(new LinuxInstallationSupplier());
            installationSuppliers.add(new OsXInstallationSupplier(os, new DefaultOsXJavaHomeCommand(execHandleFactory)));
            installationSuppliers.add(new WindowsInstallationSupplier(windowsRegistry, os));

            File rootDirectory = buildLayoutFactory.getLayoutFor(buildLayoutConfiguration).getRootDirectory();
            FileResolver fileResolver = new BaseDirFileResolver(rootDirectory);
            CurrentBuildPlatform currentBuildPlatform = new CurrentBuildPlatform(systemInfo, os);
            DefaultFilePropertyFactory filePropertyFactory = new DefaultFilePropertyFactory(propertyHost, fileResolver, fileCollectionFactory);
            DecompressionCoordinator decompressionCoordinator = new DefaultDecompressionCoordinator(scopedCacheBuilderFactory);
            Deleter deleter = new DefaultDeleter(clock::getCurrentTime, fileSystem::isSymlink, os.isWindows());
            FileOperations fileOperations = new DefaultFileOperations(fileResolver, DirectInstantiator.INSTANCE, directoryFileTreeFactory, new DefaultFileHasher(new DefaultStreamHasher()), DefaultResourceHandler.Factory.from(fileResolver, null, fileSystem, temporaryFileProvider, null), fileCollectionFactory, propertyFactory, fileSystem, PatternSet::new, deleter, documentationRegistry, DefaultTaskDependencyFactory.withNoAssociatedProject(), new DefaultProviderFactory(), decompressionCoordinator, temporaryFileProvider);
            JdkCacheDirectory jdkCacheDirectory = new DefaultJdkCacheDirectory(gradleUserHomeDirProvider, fileOperations, fileLockManager, new DefaultJvmMetadataDetector(execHandleFactory, gradleUserHomeTemporaryFileProvider), gradleUserHomeTemporaryFileProvider);
            JavaInstallationRegistry javaInstallationRegistry = new DefaultJavaInstallationRegistry(toolchainConfiguration, installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory, fileResolver, jdkCacheDirectory, new JvmInstallationProblemReporter());
            JavaToolchainHttpRedirectVerifierFactory redirectVerifierFactory = new JavaToolchainHttpRedirectVerifierFactory();
            HttpClientHelper.Factory httpClientHelperFactory = HttpClientHelper.Factory.createFactory(new DocumentationRegistry());
            ExternalResourceFactory externalResourceFactory = new DaemonToolchainExternalResourceFactory(fileSystem, listenerManager, redirectVerifierFactory, httpClientHelperFactory, progressLoggerFactory, clock, operationIdFactory, buildProgressListener);
            SecureFileDownloader secureFileDownloader = new SecureFileDownloader(externalResourceFactory);
            DaemonJavaToolchainProvisioningService javaToolchainProvisioningService = new DaemonJavaToolchainProvisioningService(secureFileDownloader, jdkCacheDirectory, currentBuildPlatform, toolchainDownloadUrlProvider, toolchainConfiguration.isDownloadEnabled(), progressLoggerFactory);
            return new JavaToolchainQueryService(jvmMetadataDetector, filePropertyFactory, javaToolchainProvisioningService, javaInstallationRegistry, null);
        });
    }
}
