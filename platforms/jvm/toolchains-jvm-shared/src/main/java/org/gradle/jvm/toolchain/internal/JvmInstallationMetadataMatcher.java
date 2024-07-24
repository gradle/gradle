/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import com.google.common.collect.ImmutableSet;
import org.gradle.internal.jvm.inspection.JavaInstallationCapability;
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.util.Set;
import java.util.function.Predicate;

public class JvmInstallationMetadataMatcher implements Predicate<JvmInstallationMetadata> {
    private final JavaLanguageVersion languageVersion;
    private final DefaultJvmVendorSpec vendorSpec;
    private final JvmImplementation jvmImplementation;
    private final Set<JavaInstallationCapability> requiredCapabilities;

    public JvmInstallationMetadataMatcher(JavaLanguageVersion languageVersion, JvmVendorSpec vendorSpec, JvmImplementation jvmImplementation, Set<JavaInstallationCapability> requiredCapabilities) {
        this.languageVersion = languageVersion;
        this.vendorSpec = (DefaultJvmVendorSpec)vendorSpec;
        this.jvmImplementation = jvmImplementation;
        this.requiredCapabilities = ImmutableSet.copyOf(requiredCapabilities);
    }

    public JvmInstallationMetadataMatcher(JavaToolchainSpec spec, Set<JavaInstallationCapability> requiredCapabilities) {
        this(spec.getLanguageVersion().get(), spec.getVendor().get(), spec.getImplementation().get(), requiredCapabilities);
    }

    @Override
    public boolean test(JvmInstallationMetadata metadata) {
        return hasMatchingMajorVersion(metadata) && vendorSpec.test(metadata) && hasRequiredCapabilities(metadata) && hasMatchingImplementation(metadata);
    }

    private boolean hasMatchingMajorVersion(JvmInstallationMetadata metadata) {
        JavaLanguageVersion actualVersion = JavaLanguageVersion.of(metadata.getJavaMajorVersion());
        return actualVersion.equals(languageVersion);
    }

    private boolean hasRequiredCapabilities(JvmInstallationMetadata metadata) {
        return metadata.getCapabilities().containsAll(requiredCapabilities);
    }

    private boolean hasMatchingImplementation(JvmInstallationMetadata metadata) {
        if (jvmImplementation == JvmImplementation.VENDOR_SPECIFIC) {
            return true;
        }

        final boolean j9Requested = isJ9ExplicitlyRequested() || isJ9RequestedViaVendor();
        final boolean isJ9Vm = metadata.getCapabilities().contains(JavaInstallationCapability.J9_VIRTUAL_MACHINE);
        return j9Requested == isJ9Vm;
    }

    private boolean isJ9ExplicitlyRequested() {
        return jvmImplementation == JvmImplementation.J9;
    }

    private boolean isJ9RequestedViaVendor() {
        return vendorSpec != DefaultJvmVendorSpec.any() && vendorSpec.test(JvmVendor.KnownJvmVendor.IBM.asJvmVendor());
    }

}
