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

import org.gradle.internal.jvm.inspection.JvmInstallationMetadata;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.util.function.Predicate;

public class JvmInstallationMetadataMatcher implements Predicate<JvmInstallationMetadata> {
    private final JavaLanguageVersion languageVersion;
    private final DefaultJvmVendorSpec vendorSpec;
    private final JvmImplementation jvmImplementation;

    public JvmInstallationMetadataMatcher(JavaLanguageVersion languageVersion, JvmVendorSpec vendorSpec, JvmImplementation jvmImplementation) {
        this.languageVersion = languageVersion;
        this.vendorSpec = (DefaultJvmVendorSpec)vendorSpec;
        this.jvmImplementation = jvmImplementation;
    }

    public JvmInstallationMetadataMatcher(JavaToolchainSpec spec) {
        this(spec.getLanguageVersion().get(), spec.getVendor().get(), spec.getImplementation().get());
    }

    @Override
    public boolean test(JvmInstallationMetadata metadata) {
        Predicate<JvmInstallationMetadata> predicate = languagePredicate().and(vendorPredicate()).and(this::implementationTest);
        return predicate.test(metadata);
    }

    private Predicate<JvmInstallationMetadata> languagePredicate() {
        return metadata -> {
            JavaLanguageVersion actualVersion = JavaToolchain.getJavaLanguageVersion(metadata);
            return actualVersion.equals(languageVersion);
        };
    }

    private boolean isJ9ExplicitlyRequested() {
        return jvmImplementation == JvmImplementation.J9;
    }

    private boolean isJ9RequestedViaVendor() {
        return vendorSpec != DefaultJvmVendorSpec.any() && vendorSpec.test(JvmVendor.KnownJvmVendor.IBM.asJvmVendor());
    }

    private Predicate<JvmInstallationMetadata> vendorPredicate() {
        return vendorSpec;
    }

    private boolean implementationTest(JvmInstallationMetadata metadata) {
        if (jvmImplementation == JvmImplementation.VENDOR_SPECIFIC) {
            return true;
        }

        final boolean j9Requested = isJ9ExplicitlyRequested() || isJ9RequestedViaVendor();
        final boolean isJ9Vm = metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE);
        return j9Requested == isJ9Vm;
    }
}
