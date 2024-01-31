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

package org.gradle.internal.buildconfiguration;

import org.gradle.api.JavaVersion;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JvmImplementation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;

public class UpdateDaemonJvmModifier extends BuildPropertiesModifier {

    public UpdateDaemonJvmModifier(@Nonnull File projectDir) {
        super(projectDir);
    }

    public void updateJvmCriteria(
        @Nonnull Integer toolchainVersion,
        @Nullable JvmVendor toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) {
        validateToolchainVersion(toolchainVersion);
        updateProperties(buildProperties -> {
            buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY, toolchainVersion.toString());
            if (toolchainVendor != null) {
                buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, toolchainVendor.getKnownVendor().name());
            } else {
                buildProperties.remove(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY);
            }
            if (toolchainImplementation != null) {
                buildProperties.put(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, toolchainImplementation.name());
            } else {
                buildProperties.remove(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY);
            }
        });
    }

    private void validateToolchainVersion(Integer version) {
        int minimumSupportedVersion = Integer.parseInt(JavaVersion.VERSION_1_8.getMajorVersion());
        int maximumSupportedVersion = Integer.parseInt(JavaVersion.VERSION_HIGHER.getMajorVersion());
        if (version < minimumSupportedVersion || version > maximumSupportedVersion) {
            String exceptionMessage = String.format("Invalid integer value %d provided for the 'toolchain-version' option. The supported values are in the range [%d, %d].",
                version, minimumSupportedVersion, maximumSupportedVersion);
            throw new IllegalArgumentException(exceptionMessage);
        }
    }
}
