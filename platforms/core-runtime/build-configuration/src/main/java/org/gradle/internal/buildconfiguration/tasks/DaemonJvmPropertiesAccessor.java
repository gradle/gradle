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

package org.gradle.internal.buildconfiguration.tasks;

import org.gradle.api.JavaVersion;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesUtils.getToolchainUrlPropertyForPlatform;
import static org.gradle.internal.buildconfiguration.resolvers.ToolchainSupportedPlatformsMatrix.getToolchainSupportedBuildPlatforms;
import static org.gradle.util.internal.GUtil.toEnum;

public class DaemonJvmPropertiesAccessor {

    private final Map<String, String> properties;

    public DaemonJvmPropertiesAccessor(Map<String, String> properties) {
        this.properties = properties;
    }

    public @Nullable JavaVersion getVersion() {
        String version = properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY);
        try {
            return version == null || version.isEmpty() ? null : JavaVersion.toVersion(version);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Value '%s' given for %s is an invalid Java version", version, DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY));
        }
    }

    public JvmVendorSpec getVendor() {
        String vendor = properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY);
        try {
            return vendor == null || vendor.isEmpty() ? DefaultJvmVendorSpec.any() : DefaultJvmVendorSpec.of(toEnum(JvmVendor.KnownJvmVendor.class, vendor));
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Value '%s' given for %s is an invalid Java vendor. Possible values are %s",
                vendor, DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, Arrays.toString(JvmVendor.KnownJvmVendor.values())));
        }
    }

    public Map<BuildPlatform, String> getToolchainDownloadUrls() {
        Map<BuildPlatform, String> toolchainDownloadUrls = new HashMap<>();
        getToolchainSupportedBuildPlatforms().forEach(buildPlatform -> {
            String toolchainDownloadUrl = properties.get(getToolchainUrlPropertyForPlatform(buildPlatform));
            if (toolchainDownloadUrl != null) {
                toolchainDownloadUrls.put(buildPlatform, toolchainDownloadUrl);
            }
        });
        return toolchainDownloadUrls;
    }
}
