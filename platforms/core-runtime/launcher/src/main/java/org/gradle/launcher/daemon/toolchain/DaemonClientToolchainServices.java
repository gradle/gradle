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

import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider;
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.jvm.inspection.DefaultJavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationProblemReporter;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultOsXJavaHomeCommand;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.DefaultJdkCacheDirectory;

import java.util.List;

public class DaemonClientToolchainServices implements ServiceRegistrationProvider {

    private final ToolchainConfiguration toolchainConfiguration;

    public DaemonClientToolchainServices(ToolchainConfiguration toolchainConfiguration) {
        this.toolchainConfiguration = toolchainConfiguration;
    }

    public void configure(ServiceRegistration registration) {
        registration.add(ToolchainConfiguration.class, toolchainConfiguration);
        registration.add(DefaultOsXJavaHomeCommand.class);

        // NOTE: These need to be kept in sync with ToolchainsJvmServices
        registration.add(AsdfInstallationSupplier.class);
        registration.add(IntellijInstallationSupplier.class);
        registration.add(JabbaInstallationSupplier.class);
        registration.add(SdkmanInstallationSupplier.class);

//        registration.add(MavenToolchainsInstallationSupplier.class);

        registration.add(LinuxInstallationSupplier.class);
        registration.add(OsXInstallationSupplier.class);
        registration.add(WindowsInstallationSupplier.class);
    }

    @Provides
    protected DaemonJavaToolchainQueryService createDaemonJavaToolchainQueryService(JavaInstallationRegistry javaInstallationRegistry) {
        return new DaemonJavaToolchainQueryService(javaInstallationRegistry);
    }

    @Provides
    protected JavaInstallationRegistry createJavaInstallationRegistry(ToolchainConfiguration toolchainConfiguration, List<InstallationSupplier> installationSuppliers, JvmMetadataDetector jvmMetadataDetector, ProgressLoggerFactory progressLoggerFactory, FileResolver fileResolver, JdkCacheDirectory jdkCacheDirectory) {
        return new DefaultJavaInstallationRegistry(toolchainConfiguration, installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory, fileResolver, jdkCacheDirectory, new JvmInstallationProblemReporter());
    }

    @Provides
    protected JdkCacheDirectory createJdkCacheDirectory(GradleUserHomeDirProvider gradleUserHomeDirProvider, FileLockManager fileLockManager, JvmMetadataDetector jvmMetadataDetector, GradleUserHomeTemporaryFileProvider gradleUserHomeTemporaryFileProvider) {
        return new DefaultJdkCacheDirectory(gradleUserHomeDirProvider, null, fileLockManager, jvmMetadataDetector, gradleUserHomeTemporaryFileProvider);
    }
}
