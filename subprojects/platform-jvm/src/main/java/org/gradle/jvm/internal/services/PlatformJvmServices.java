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

import com.google.common.collect.ImmutableList;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainCandidate;
import org.gradle.jvm.toolchain.JavaToolchainProvisioningDetails;
import org.gradle.jvm.toolchain.JavaToolchainProvisioningService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.install.internal.AdoptOpenJdkRemoteProvisioningService;
import org.gradle.jvm.toolchain.install.internal.DefaultJavaToolchainInstallationService;
import org.gradle.jvm.toolchain.install.internal.JdkCacheDirectory;
import org.gradle.jvm.toolchain.install.internal.JdkDownloader;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainService;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JavaInstallationRegistry;
import org.gradle.jvm.toolchain.internal.JavaToolchainFactory;
import org.gradle.jvm.toolchain.internal.JavaToolchainQueryService;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ProvisioningServicesRegistry;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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
        registration.add(DefaultJavaToolchainInstallationService.class);
        registration.addProvider(new Object() {
            ProvisioningServicesRegistry createProvisioningServicesRegistry() {
                return new ProvisioningServicesRegistry() {
                    private final Deque<JavaToolchainProvisioningService> services = new ArrayDeque<>();

                    @Override
                    public void registerProvisioningService(JavaToolchainProvisioningService service) {
                        services.addFirst(service);
                    }

                    @Override
                    public List<JavaToolchainProvisioningService> getProvisioningServices() {
                        return ImmutableList.copyOf(services);
                    }
                };
            }

            JavaToolchainProvisioningService createToolchainProvisioningService(JdkDownloader downloader, ProviderFactory providerFactory, ProvisioningServicesRegistry registry) {
                AdoptOpenJdkRemoteProvisioningService adopt = new AdoptOpenJdkRemoteProvisioningService(downloader, providerFactory);
                registry.registerProvisioningService(adopt);
                return new FirstMatchingToolchainProvisioningService(registry);
            }
        });
        registration.add(JdkDownloader.class);
        registration.add(JavaToolchainQueryService.class);
        registration.add(DefaultJavaToolchainService.class);
    }

    private static class FirstMatchingToolchainProvisioningService implements JavaToolchainProvisioningService {
        private final ProvisioningServicesRegistry services;

        private FirstMatchingToolchainProvisioningService(ProvisioningServicesRegistry services) {
            this.services = services;
        }

        @Override
        public void findCandidates(JavaToolchainProvisioningDetails details) {
            for (JavaToolchainProvisioningService service : services.getProvisioningServices()) {
                AtomicBoolean listed = new AtomicBoolean();
                service.findCandidates(new JavaToolchainProvisioningDetails() {
                    @Override
                    public JavaToolchainSpec getRequested() {
                        return details.getRequested();
                    }

                    @Override
                    public JavaToolchainCandidate.Builder newCandidate() {
                        return details.newCandidate();
                    }

                    @Override
                    public void listCandidates(List<JavaToolchainCandidate> candidates) {
                        listed.set(true);
                        details.listCandidates(
                            candidates.stream()
                                .map(c -> new WrappedJavaToolchainCandidate(service, c))
                                .collect(Collectors.toList())
                        );
                    }

                    @Override
                    public String getOperatingSystem() {
                        return details.getOperatingSystem();
                    }

                    @Override
                    public String getSystemArch() {
                        return details.getSystemArch();
                    }
                });
                if (listed.get()) {
                    return;
                }
            }
        }

        @Override
        public LazyProvisioner provisionerFor(JavaToolchainCandidate candidate) {
            WrappedJavaToolchainCandidate wrapped = (WrappedJavaToolchainCandidate) candidate;
            return wrapped.origin.provisionerFor(wrapped.delegate);
        }
    }

    private static class WrappedJavaToolchainCandidate implements JavaToolchainCandidate {
        private final JavaToolchainProvisioningService origin;
        private final JavaToolchainCandidate delegate;

        private WrappedJavaToolchainCandidate(JavaToolchainProvisioningService origin, JavaToolchainCandidate delegate) {
            this.origin = origin;
            this.delegate = delegate;
        }

        @Override
        public JavaLanguageVersion getLanguageVersion() {
            return delegate.getLanguageVersion();
        }

        @Override
        public String getVendor() {
            return delegate.getVendor();
        }

        @Override
        public Optional<JvmImplementation> getImplementation() {
            return delegate.getImplementation();
        }

        @Override
        public String getArch() {
            return delegate.getArch();
        }

        @Override
        public String getOperatingSystem() {
            return delegate.getOperatingSystem();
        }
    }

}
