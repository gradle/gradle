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

import org.gradle.internal.jvm.inspection.ConditionalInvalidation;
import org.gradle.internal.jvm.inspection.InvalidJvmInstallationCacheInvalidator;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmMetadataDetector;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.jvm.toolchain.internal.AsdfInstallationSupplier;
import org.gradle.jvm.toolchain.internal.CurrentInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.JabbaInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LinuxInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.MavenToolchainsInstallationSupplier;
import org.gradle.jvm.toolchain.internal.OsXInstallationSupplier;
import org.gradle.jvm.toolchain.internal.SdkmanInstallationSupplier;
import org.gradle.jvm.toolchain.internal.WindowsInstallationSupplier;

public class PlatformJvmServices extends AbstractPluginServiceRegistry {
    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registerJavaInstallationSuppliers(registration);
        registerInvalidJavaInstallationsCacheInvalidator(registration);
    }

    private void registerJavaInstallationSuppliers(ServiceRegistration registration) {
        registration.add(AsdfInstallationSupplier.class);
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
