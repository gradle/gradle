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

import com.google.common.base.MoreObjects;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.Objects;

public class DefaultToolchainSpec implements JavaToolchainSpecInternal {

    public static class Key implements JavaToolchainSpecInternal.Key {
        private final JavaLanguageVersion languageVersion;
        private final JvmVendorSpec vendor;
        private final JvmImplementation implementation;

        public Key(@Nullable JavaLanguageVersion languageVersion, @Nullable JvmVendorSpec vendor, @Nullable JvmImplementation implementation) {
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
            Key that = (Key) o;
            return Objects.equals(languageVersion, that.languageVersion)
                && Objects.equals(vendor, that.vendor)
                && Objects.equals(implementation, that.implementation);
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

    private final Property<JavaLanguageVersion> languageVersion;
    private final Property<JvmVendorSpec> vendor;
    private final Property<JvmImplementation> implementation;

    @Inject
    public DefaultToolchainSpec(ObjectFactory factory) {
        this.languageVersion = factory.property(JavaLanguageVersion.class);
        this.vendor = factory.property(JvmVendorSpec.class).convention(getConventionVendor());
        this.implementation = factory.property(JvmImplementation.class).convention(getConventionImplementation());
    }

    @Override
    public Property<JavaLanguageVersion> getLanguageVersion() {
        return languageVersion;
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
    public JavaToolchainSpecInternal.Key toKey() {
        return new Key(languageVersion.getOrNull(), vendor.getOrNull(), implementation.getOrNull());
    }

    @Override
    public boolean isConfigured() {
        return languageVersion.isPresent();
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean isValid() {
        if (vendor.getOrNull() == JvmVendorSpec.IBM_SEMERU) {
            // https://github.com/gradle/gradle/issues/23155
            // This should make the spec invalid when the enum gets removed
            DeprecationLogger.deprecateBehaviour("Requesting JVM vendor IBM_SEMERU.")
                .willBeRemovedInGradle9()
                .withUpgradeGuideSection(8, "ibm_semeru_should_not_be_used")
                .nagUser();
        }
        return languageVersion.isPresent() || isSecondaryPropertiesUnchanged();
    }

    private boolean isSecondaryPropertiesUnchanged() {
        return Objects.equals(getConventionVendor(), vendor.getOrNull()) &&
            Objects.equals(getConventionImplementation(), implementation.getOrNull());
    }

    @Override
    public String getDisplayName() {
        final MoreObjects.ToStringHelper builder = MoreObjects.toStringHelper("");
        builder.omitNullValues();
        builder.add("languageVersion", languageVersion.map(JavaLanguageVersion::toString).getOrElse("unspecified"));
        builder.add("vendor", vendor.map(JvmVendorSpec::toString).getOrNull());
        builder.add("implementation", implementation.map(JvmImplementation::toString).getOrNull());
        return builder.toString();
    }

    @Override
    public void finalizeProperties() {
        getLanguageVersion().finalizeValue();
        getVendor().finalizeValue();
        getImplementation().finalizeValue();
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static JvmVendorSpec getConventionVendor() {
        return DefaultJvmVendorSpec.any();
    }

    private static JvmImplementation getConventionImplementation() {
        return JvmImplementation.VENDOR_SPECIFIC;
    }
}
