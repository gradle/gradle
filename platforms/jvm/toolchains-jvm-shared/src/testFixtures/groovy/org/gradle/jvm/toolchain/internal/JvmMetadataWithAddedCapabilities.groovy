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

package org.gradle.jvm.toolchain.internal

import com.google.common.collect.ImmutableSet
import com.google.common.collect.Sets
import org.gradle.internal.jvm.inspection.JavaInstallationCapability
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata

/**
 * Implements a delegating JvmInstallationMetadata but with additional capabilities beyond what the original already contains.
 *
 * <p>
 * In particular, this is useful for adding JDK capabilities to what would normally be a JRE installation, as creating files in the required locations would be verbose in tests.
 * </p>
 */
class JvmMetadataWithAddedCapabilities implements JvmInstallationMetadata {
    @Delegate
    private final JvmInstallationMetadata metadata
    private final Set<JavaInstallationCapability> additionalCapabilities

    JvmMetadataWithAddedCapabilities(JvmInstallationMetadata metadata, Set<JavaInstallationCapability> additionalCapabilities) {
        this.metadata = metadata
        this.additionalCapabilities = ImmutableSet.copyOf(additionalCapabilities)
    }

    @Override
    Set<JavaInstallationCapability> getCapabilities() {
        EnumSet<JavaInstallationCapability> capabilities = EnumSet.copyOf(metadata.getCapabilities())
        capabilities.addAll(additionalCapabilities)
        return Sets.immutableEnumSet(capabilities)
    }
}
