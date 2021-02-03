/*
 * Copyright 2014 the original author or authors.
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

import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.toolchain.install.internal.AdoptOpenJdkDownloader;
import org.gradle.jvm.toolchain.install.internal.AdoptOpenJdkRemoteBinary;
import org.gradle.jvm.toolchain.install.internal.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.install.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaToolchainFactory;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaInstallationRegistry;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;

public class PlatformJvmServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(JdkCacheDirectory.class);
        registration.add(JavaInstallationRegistry.class);
        registerJavaInstallationSuppliers(registration);
    }

    private void registerJavaInstallationSuppliers(ServiceRegistration registration) {
        registration.add(AsdfInstallationSupplier.class);
        registration.add(AutoInstalledInstallationSupplier.class);
        registration.add(CurrentInstallationSupplier.class);
        registration.add(EnvironmentVariableListInstallationSupplier.class);
        registration.add(JabbaInstallationSupplier.class);
        registration.add(LinuxInstallationSupplier.class);
        registration.add(LocationListInstallationSupplier.class);
        registration.add(OsXInstallationSupplier.class);
        registration.add(SdkmanInstallationSupplier.class);
        registration.add(WindowsInstallationSupplier.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(JavaToolchainFactory.class);
        registration.add(DefaultJavaToolchainProvisioningService.class);
        registration.add(AdoptOpenJdkRemoteBinary.class);
        registration.add(AdoptOpenJdkDownloader.class);
        registration.add(JavaToolchainQueryService.class);
        registration.add(DefaultJavaToolchainService.class);
    }

}
