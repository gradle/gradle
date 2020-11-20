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
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;

import java.util.function.Predicate;

public class ToolchainMatcher implements Predicate<JavaToolchain> {

    private final JavaToolchainSpec filter;

    public ToolchainMatcher(JavaToolchainSpec filter) {
        this.filter = filter;
    }

    @Override
    public boolean test(JavaToolchain toolchain) {
        return languagePredicate().and(vendorPredicate()).and(implementationPredicate()).test(toolchain);
    }

    private Predicate<JavaToolchain> languagePredicate() {
        return toolchain -> toolchain.getLanguageVersion().equals(filter.getLanguageVersion().get());
    }

    private Predicate<? super JavaToolchain> implementationPredicate() {
        return toolchain -> {
            final boolean isJ9Vm = toolchain.getMetadata().hasCapability(JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE);
            final boolean j9Requested = filter.getImplementation().get() == JvmImplementation.J9;
            return j9Requested == isJ9Vm;
        };
    }

    @SuppressWarnings("unchecked")
    private Predicate<? super JavaToolchain> vendorPredicate() {
        return (Predicate<? super JavaToolchain>) filter.getVendor().get();
    }
}
