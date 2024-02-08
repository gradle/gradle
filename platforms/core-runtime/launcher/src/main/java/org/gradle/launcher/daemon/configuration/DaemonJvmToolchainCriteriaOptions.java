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

package org.gradle.launcher.daemon.configuration;

import com.google.common.collect.ImmutableList;
import org.gradle.internal.buildconfiguration.BuildPropertiesDefaults;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.EnumBuildOption;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.PropertyOrigin;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;

import java.util.List;

public class DaemonJvmToolchainCriteriaOptions extends BuildOptionSet<DaemonJvmToolchainSpec> {

    private static List<BuildOption<DaemonJvmToolchainSpec>> options = ImmutableList.of(
        new ToolchainVersionOption(),
        new ToolchainVendorOption(),
        new ToolchainImplementationOption()
    );

    public static List<BuildOption<DaemonJvmToolchainSpec>> get() {
        return options;
    }

    @Override
    public List<BuildOption<DaemonJvmToolchainSpec>> getAllOptions() {
        return options;
    }

    public static class ToolchainVersionOption extends StringBuildOption<DaemonJvmToolchainSpec> {

        public ToolchainVersionOption() {
            super(BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY, PropertyOrigin.BUILD_PROPERTIES);
        }

        @Override
        public void applyTo(String value, DaemonJvmToolchainSpec settings, Origin origin) {
            try {
                int integerValue = Integer.parseInt(value);
                settings.getLanguageVersion().set(JavaLanguageVersion.of(integerValue));
            } catch (NumberFormatException e) {
                origin.handleInvalidValue(value, "the value should be an int");
            } catch (IllegalArgumentException e) {
                origin.handleInvalidValue(value, "the value should be a positive int");
            }
        }
    }

    public static class ToolchainVendorOption extends EnumBuildOption<JvmVendor.KnownJvmVendor, DaemonJvmToolchainSpec> {

        public ToolchainVendorOption() {
            super(BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, JvmVendor.KnownJvmVendor.class, JvmVendor.KnownJvmVendor.values(), BuildPropertiesDefaults.TOOLCHAIN_VENDOR_PROPERTY, PropertyOrigin.BUILD_PROPERTIES);
        }

        @Override
        public void applyTo(JvmVendor.KnownJvmVendor value, DaemonJvmToolchainSpec settings, Origin origin) {
            assertJvmToolchainVersion(settings);
            settings.getVendor().set(DefaultJvmVendorSpec.of(value));
        }
    }

    public static class ToolchainImplementationOption extends EnumBuildOption<JvmImplementation, DaemonJvmToolchainSpec> {

        public ToolchainImplementationOption() {
            super(BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, JvmImplementation.class, JvmImplementation.values(), BuildPropertiesDefaults.TOOLCHAIN_IMPLEMENTATION_PROPERTY, PropertyOrigin.BUILD_PROPERTIES);
        }

        @Override
        public void applyTo(JvmImplementation value, DaemonJvmToolchainSpec settings, Origin origin) {
            assertJvmToolchainVersion(settings);
            settings.getImplementation().set(value);
        }
    }

    private static void assertJvmToolchainVersion(DaemonJvmToolchainSpec settings) {
        if (!settings.getLanguageVersion().isPresent()) {
            String exceptionMessage = String.format("Option %s undefined on Build properties. " +
                "Execute 'updateDaemonJvm' task with desired criteria to fix it.", BuildPropertiesDefaults.TOOLCHAIN_VERSION_PROPERTY);
            throw new IllegalArgumentException(exceptionMessage);
        }
    }
}
