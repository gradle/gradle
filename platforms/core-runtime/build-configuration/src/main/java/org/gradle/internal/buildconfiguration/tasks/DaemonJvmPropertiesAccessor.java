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

import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaLanguageVersion;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.stream.Collectors;

import static org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesUtils.getPlatformFromToolchainProperty;

public class DaemonJvmPropertiesAccessor {

    private final Map<String, String> properties;

    public DaemonJvmPropertiesAccessor(Map<String, String> properties) {
        this.properties = properties;
    }

    public @Nullable JavaLanguageVersion getVersion() {
        String requestedVersion = properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY);
        if (requestedVersion == null) {
            return null;
        }

        try {
            return DefaultJavaLanguageVersion.fromFullVersion(requestedVersion);
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Value '%s' given for %s is an invalid Java version", requestedVersion, DaemonJvmPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY));
        }
    }

    public JvmVendorSpec getVendor() {
        String requestedVendor = properties.get(DaemonJvmPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY);
        if (requestedVendor != null) {
            return JvmVendorSpec.of(requestedVendor);
        } else {
            // match any vendor
            return DefaultJvmVendorSpec.any();
        }
    }

    public Map<BuildPlatform, String> getToolchainDownloadUrls() {
        return properties.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(DaemonJvmPropertiesDefaults.TOOLCHAIN_URL_PROPERTY_PREFIX))
                .collect(Collectors.toMap(entry -> getPlatformFromToolchainProperty(entry.getKey()), Map.Entry::getValue));
    }
}
