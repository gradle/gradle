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

package org.gradle.launcher.daemon.jvm;

import com.google.common.collect.Lists;
import net.rubygrapefruit.platform.WindowsRegistry;
import org.gradle.StartParameter;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.IdentityFileResolver;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.process.internal.ExecFactory;

import java.util.List;

import static org.gradle.jvm.toolchain.internal.AutoDetectingInstallationSupplier.AUTO_DETECT;
import static org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier.AUTO_DOWNLOAD;

public class DaemonJavaInstallationRegistryFactory implements JavaInstallationRegistryFactory {

    private final JdkCacheDirectory jdkCacheDirectory;
    private final JvmMetadataDetector jvmMetadataDetector;
    private final ExecFactory execFactory;
    private final ProgressLoggerFactory progressLoggerFactory;
    private final WindowsRegistry windowsRegistry;

    public DaemonJavaInstallationRegistryFactory(JdkCacheDirectory jdkCacheDirectory, JvmMetadataDetector jvmMetadataDetector, ExecFactory execFactory, ProgressLoggerFactory progressLoggerFactory, WindowsRegistry windowsRegistry) {
        this.jdkCacheDirectory = jdkCacheDirectory;
        this.jvmMetadataDetector = jvmMetadataDetector;
        this.execFactory = execFactory;
        this.progressLoggerFactory = progressLoggerFactory;
        this.windowsRegistry = windowsRegistry;
    }

    @Override
    public JavaInstallationRegistry getRegistry(StartParameter startParameter) {
        List<InstallationSupplier> installationSuppliers = getInstallationSuppliers(startParameter);
        return new JavaInstallationRegistry(installationSuppliers, jvmMetadataDetector, null, OperatingSystem.current(), progressLoggerFactory);
    }

    private List<InstallationSupplier> getInstallationSuppliers(StartParameter startParameter) {
        DaemonProviderFactory providerFactory = new DaemonProviderFactory(startParameter);
        // The gradle property 'org.gradle.java.installations.auto-detect' must be ignored for Daemon Toolchain
        providerFactory.overrideProperty(AUTO_DETECT, true);
        // The gradle property 'org.gradle.java.installations.auto-download' must be ignored for Daemon Toolchain
        providerFactory.overrideProperty(AUTO_DOWNLOAD, true);

        FileResolver resolver = new IdentityFileResolver();
        return Lists.newArrayList(
            new AutoInstalledInstallationSupplier(providerFactory, jdkCacheDirectory),
            new AsdfInstallationSupplier(providerFactory),
            new CurrentInstallationSupplier(providerFactory),
            new IntellijInstallationSupplier(providerFactory, resolver),
            new JabbaInstallationSupplier(providerFactory),
            new LinuxInstallationSupplier(providerFactory),
            new MavenToolchainsInstallationSupplier(providerFactory, resolver),
            new OsXInstallationSupplier(execFactory, providerFactory, OperatingSystem.current()),
            new SdkmanInstallationSupplier(providerFactory),
            new WindowsInstallationSupplier(windowsRegistry, OperatingSystem.current(), providerFactory)
        );
    }
}
