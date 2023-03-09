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

import net.rubygrapefruit.platform.SystemInfo;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.cache.FileLockManager;
import org.gradle.initialization.GradleUserHomeDirProvider;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.jvm.inspection.ConditionalInvalidation;
import org.gradle.internal.jvm.inspection.InvalidJvmInstallationCacheInvalidator;
import org.gradle.internal.jvm.inspection.JavaInstallationRegistry;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.DefaultJvmToolchainManagement;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.InstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.install.DefaultJavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.internal.install.JdkCacheDirectory;
import org.gradle.jvm.toolchain.internal.install.SecureFileDownloader;
import org.gradle.platform.internal.DefaultBuildPlatform;

import java.util.List;

public class PlatformJvmServices extends AbstractPluginServiceRegistry {

    protected static class BuildServices {

        protected DefaultBuildPlatform createBuildPlatform(ObjectFactory objectFactory, SystemInfo systemInfo, OperatingSystem operatingSystem) {
            return objectFactory.newInstance(DefaultBuildPlatform.class, systemInfo, operatingSystem);
        }

        protected DefaultJavaToolchainResolverRegistry createJavaToolchainResolverRegistry(
                Gradle gradle,
                Instantiator instantiator,
                ObjectFactory objectFactory,
                ProviderFactory providerFactory,
                AuthenticationSchemeRegistry authenticationSchemeRegistry) {
            return objectFactory.newInstance(DefaultJavaToolchainResolverRegistry.class, gradle, instantiator, objectFactory, providerFactory, authenticationSchemeRegistry);
        }

        protected DefaultJvmToolchainManagement createToolchainManagement(ObjectFactory objectFactory, JavaToolchainResolverRegistry registry) {
            return objectFactory.newInstance(DefaultJvmToolchainManagement.class, registry);
        }

        protected JdkCacheDirectory createJdkCacheDirectory(ObjectFactory objectFactory, GradleUserHomeDirProvider homeDirProvider, FileOperations operations, FileLockManager lockManager, JvmMetadataDetector detector) {
            return objectFactory.newInstance(JdkCacheDirectory.class, homeDirProvider, operations, lockManager, detector);
        }

        protected JavaInstallationRegistry createJavaInstallationRegistry(ObjectFactory objectFactory, List<InstallationSupplier> suppliers, BuildOperationExecutor executor, OperatingSystem os) {
            return objectFactory.newInstance(JavaInstallationRegistry.class, suppliers, executor, os);
        }

    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.addProvider(new BuildServices());
        registerJavaInstallationSuppliers(registration);
        registerInvalidJavaInstallationsCacheInvalidator(registration);
    }

    private void registerJavaInstallationSuppliers(ServiceRegistration registration) {
        registration.add(AsdfInstallationSupplier.class);
        registration.add(AutoInstalledInstallationSupplier.class);
        registration.add(CurrentInstallationSupplier.class);
        registration.add(EnvironmentVariableListInstallationSupplier.class);
        registration.add(IntellijInstallationSupplier.class);
        registration.add(JabbaInstallationSupplier.class);
        registration.add(LinuxInstallationSupplier.class);
        registration.add(LocationListInstallationSupplier.class);
        registration.add(MavenToolchainsInstallationSupplier.class);
        registration.add(OsXInstallationSupplier.class);
        registration.add(SdkmanInstallationSupplier.class);
        registration.add(WindowsInstallationSupplier.class);
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
        registration.add(DefaultJavaToolchainProvisioningService.class);
        registration.add(SecureFileDownloader.class);
        registration.add(JavaToolchainQueryService.class);
        registration.add(DefaultJavaToolchainService.class);
    }

    private void registerInvalidJavaInstallationsCacheInvalidator(ServiceRegistration registration) {
        registration.addProvider(new Object() {
            public void configure(ServiceRegistration serviceRegistration, JvmMetadataDetector globalJvmMetadataDetector) {
                if (globalJvmMetadataDetector instanceof ConditionalInvalidation) {
                    // Avoiding generic-unchecked cast with this intermediate implementation that checks the types of the items:
                    ConditionalInvalidation<JvmInstallationMetadata> checkedInvalidationFromDetector =
                        predicate -> ((ConditionalInvalidation<?>) globalJvmMetadataDetector).invalidateItemsMatching(item ->
                            item instanceof JvmInstallationMetadata && predicate.test((JvmInstallationMetadata) item)
                        );
                    serviceRegistration.add(
                        InvalidJvmInstallationCacheInvalidator.class,
                        new InvalidJvmInstallationCacheInvalidator(checkedInvalidationFromDetector)
                    );
                }
            }
        });
    }
}
