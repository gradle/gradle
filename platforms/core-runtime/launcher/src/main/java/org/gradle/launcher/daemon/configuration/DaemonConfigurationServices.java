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

package org.gradle.launcher.daemon.configuration;

import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.progress.DefaultProgressLoggerFactory;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.logging.services.ProgressLoggingBridge;
import org.gradle.internal.operations.DefaultBuildOperationIdFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.time.Clock;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.launcher.daemon.jvm.DaemonJavaToolchainQueryService;

import java.util.List;

public class DaemonConfigurationServices {
    protected DaemonJavaToolchainQueryService createDaemonJavaToolchainQueryService(JavaInstallationRegistry javaInstallationRegistry) {
        return new DaemonJavaToolchainQueryService(javaInstallationRegistry);
    }
    protected JavaInstallationRegistry createJavaInstallationRegistry(List<InstallationSupplier> installationSuppliers, JvmMetadataDetector jvmMetadataDetector, ProgressLoggerFactory progressLoggerFactory) {
        return new JavaInstallationRegistry(installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory, null);
    }
    protected InstallationSupplier createAutoInstalledInstallationSupplier(JdkCacheDirectory jdkCacheDirectory) {
        return new AutoInstalledInstallationSupplier(jdkCacheDirectory);
    }
    protected InstallationSupplier createCurrentInstallationSupplier() {
        return new CurrentInstallationSupplier();
    }

    // TODO: Decide if we should use all of the different installation suppliers for Gradle daemons or not

    ProgressLoggerFactory createProgressLoggerFactory(OutputEventListener outputEventListener, Clock clock) {
        return new DefaultProgressLoggerFactory(
            new ProgressLoggingBridge(outputEventListener),
            clock,
            new DefaultBuildOperationIdFactory()
        );
    }

    JdkCacheDirectory createJdkCacheDirectory(GradleUserHomeDirProvider gradleUserHomeDirProvider, FileLockManager fileLockManager, JvmMetadataDetector jvmMetadataDetector) {
        return new JdkCacheDirectory(gradleUserHomeDirProvider, null, fileLockManager, jvmMetadataDetector);
    }
}
