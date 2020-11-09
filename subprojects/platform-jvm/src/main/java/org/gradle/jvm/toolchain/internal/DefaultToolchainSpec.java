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
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;

public class DefaultToolchainSpec implements JavaToolchainSpec {

    private final Property<JavaLanguageVersion> languageVersion;
    private final Property<JvmVendorSpec> vendor;

    @Inject
    public DefaultToolchainSpec(ObjectFactory factory) {
        this.languageVersion = factory.property(JavaLanguageVersion.class);
        this.vendor = factory.property(JvmVendorSpec.class).convention(DefaultJvmVendorSpec.ANY);
    }

    @Override
    public Property<JavaLanguageVersion> getLanguageVersion() {
        return languageVersion;
    }

    @Override
    public Property<JvmVendorSpec> getVendor() {
        return vendor;
    }

    public boolean isConfigured() {
        return languageVersion.isPresent();
    }

    @Override
    public String getDisplayName() {
        final MoreObjects.ToStringHelper builder = MoreObjects.toStringHelper("");
        builder.omitNullValues();
        builder.add("languageVersion", languageVersion.map(JavaLanguageVersion::toString).getOrElse("unspecified"));
        builder.add("vendor", vendor.map(JvmVendorSpec::toString).getOrNull());
        return builder.toString();
    }

}
