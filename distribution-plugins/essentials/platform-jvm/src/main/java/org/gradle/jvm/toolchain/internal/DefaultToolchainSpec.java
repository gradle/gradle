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
import com.google.common.base.Objects;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

public class DefaultToolchainSpec implements ToolchainSpecInternal {

    private final Property<JavaLanguageVersion> languageVersion;
    private final Property<JvmVendorSpec> vendor;
    private final Property<JvmImplementation> implementation;

    @Inject
    public DefaultToolchainSpec(ObjectFactory factory) {
        this.languageVersion = factory.property(JavaLanguageVersion.class);
        this.vendor = factory.property(JvmVendorSpec.class).convention(DefaultJvmVendorSpec.any());
        this.implementation = factory.property(JvmImplementation.class).convention(JvmImplementation.VENDOR_SPECIFIC);
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
    public boolean isConfigured() {
        return languageVersion.isPresent();
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
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultToolchainSpec that = (DefaultToolchainSpec) o;
        return Objects.equal(languageVersion.get(), that.languageVersion.get()) &&
            Objects.equal(vendor.get(), that.vendor.get()) &&
            Objects.equal(implementation.get(), that.implementation.get());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(languageVersion.get(), vendor.get(), implementation.get());
    }

}
