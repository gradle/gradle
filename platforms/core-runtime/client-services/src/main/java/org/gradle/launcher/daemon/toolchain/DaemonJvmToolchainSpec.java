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

package org.gradle.launcher.daemon.toolchain;

import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainSpecInternal;

import java.util.Objects;

public class DaemonJvmToolchainSpec implements JavaToolchainSpecInternal {

    private final Property<JavaLanguageVersion> version;
    private final Property<JvmVendorSpec> vendor;
    private final Property<JvmImplementation> implementation;

    public static class Key implements JavaToolchainSpecInternal.Key {
        private final JavaLanguageVersion languageVersion;
        private final JvmVendorSpec vendor;
        private final JvmImplementation implementation;

        public Key(JavaLanguageVersion languageVersion, JvmVendorSpec vendor, JvmImplementation implementation) {
            this.languageVersion = languageVersion;
            this.vendor = vendor;
            this.implementation = implementation;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            DaemonJvmToolchainSpec.Key that = (DaemonJvmToolchainSpec.Key) o;
            return Objects.equals(languageVersion, that.languageVersion) && Objects.equals(vendor, that.vendor) && Objects.equals(implementation, that.implementation);
        }

        @Override
        public int hashCode() {
            return Objects.hash(languageVersion, vendor, implementation);
        }

        @Override
        public String toString() {
            return "DefaultKey{" +
                "languageVersion=" + languageVersion +
                ", vendor=" + vendor +
                ", implementation=" + implementation +
                '}';
        }
    }

    public DaemonJvmToolchainSpec(PropertyFactory propertyFactory, DaemonJvmCriteria.Spec daemonJvmCriteria) {
        version = propertyFactory.property(JavaLanguageVersion.class).value(JavaLanguageVersion.of(daemonJvmCriteria.getJavaVersion().asInt()));
        vendor = propertyFactory.property(JvmVendorSpec.class).value(daemonJvmCriteria.getVendorSpec());
        implementation = propertyFactory.property(JvmImplementation.class).convention(JvmImplementation.VENDOR_SPECIFIC);
    }

    @Override
    public Property<JavaLanguageVersion> getLanguageVersion() {
        return version;
    }

    @Override
    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    @Override
    public Property<JvmImplementation> getImplementation() {
        return implementation;
    }

    @Override
    public String toString() {
        return String.format("Compatible with Java %s, %s (from %s)", version.getOrNull(), vendor.getOrNull(), DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE);
    }

    @Override
    public Key toKey() {
        return new DaemonJvmToolchainSpec.Key(getLanguageVersion().get(), getVendor().get(), getImplementation().get());
    }
}
