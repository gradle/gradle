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
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import java.util.function.Predicate;

public class JavaToolchainMatcher implements Predicate<JavaToolchain> {

    private final JavaToolchainSpec spec;

    public JavaToolchainMatcher(JavaToolchainSpec spec) {
        this.spec = spec;
    }

    @Override
    public boolean test(JavaToolchain toolchain) {
        return test(toolchain.getMetadata());
    }

    public boolean test(JvmInstallationMetadata metadata) {
        Predicate<JvmInstallationMetadata> predicate = languagePredicate().and(vendorPredicate()).and(implementationPredicate());
        return predicate.test(metadata);
    }

    private Predicate<JvmInstallationMetadata> languagePredicate() {
        return metadata -> {
            JavaLanguageVersion actualVersion = JavaToolchain.getJavaLanguageVersion(metadata);
            JavaLanguageVersion expectedVersion = spec.getLanguageVersion().get();
            return actualVersion.equals(expectedVersion);
        };
    }

    private Predicate<? super JvmInstallationMetadata> implementationPredicate() {
        return metadata -> {
            final boolean isJ9Vm = metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE);
            final boolean j9Requested = spec.getImplementation().get() == JvmImplementation.J9;
            return j9Requested == isJ9Vm;
        };
    }

    @SuppressWarnings("unchecked")
    private Predicate<? super JvmInstallationMetadata> vendorPredicate() {
        JvmVendorSpec vendorSpec = spec.getVendor().get();
        return (Predicate<? super JvmInstallationMetadata>) vendorSpec;
    }
}
