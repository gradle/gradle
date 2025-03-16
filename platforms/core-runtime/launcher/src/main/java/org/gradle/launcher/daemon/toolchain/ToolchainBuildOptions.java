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

import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ToolchainBuildOptions extends BuildOptionSet<ToolchainConfiguration> {
    private final List<? extends BuildOption<? super ToolchainConfiguration>> options = Arrays.asList(
        new JavaInstallationPathsOption(),
        new JavaInstallationEnvironmentPathsOption(),
        new AutoDetectionOption(),
        new AutoDownloadOption(),
        new IntellijJdkBuildOption()
    );

    @Override
    public List<? extends BuildOption<? super ToolchainConfiguration>> getAllOptions() {
        return options;
    }

    private static class JavaInstallationPathsOption extends StringBuildOption<ToolchainConfiguration> {
        private static final String GRADLE_PROPERTY = LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY;

        public JavaInstallationPathsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
            settings.setInstallationsFromPaths(Arrays.asList(value.split(",")));
        }
    }

    private static class JavaInstallationEnvironmentPathsOption extends StringBuildOption<ToolchainConfiguration> {
        private static final String GRADLE_PROPERTY = EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY;

        public JavaInstallationEnvironmentPathsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
            settings.setJavaInstallationsFromEnvironment(Arrays.asList(value.split(",")));
        }
    }
    private static class AutoDetectionOption extends BooleanBuildOption<ToolchainConfiguration> {
        private static final String GRADLE_PROPERTY = ToolchainConfiguration.AUTO_DETECT;

        public AutoDetectionOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, ToolchainConfiguration settings, Origin origin) {
            settings.setAutoDetectEnabled(value);
        }
    }
    private static class AutoDownloadOption extends BooleanBuildOption<ToolchainConfiguration> {
        private static final String GRADLE_PROPERTY = AutoInstalledInstallationSupplier.AUTO_DOWNLOAD;

        public AutoDownloadOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(boolean value, ToolchainConfiguration settings, Origin origin) {
            settings.setDownloadEnabled(value);
        }
    }
    private static class IntellijJdkBuildOption extends StringBuildOption<ToolchainConfiguration> {
        private static final String GRADLE_PROPERTY = "org.gradle.java.installations.idea-jdks-directory";

        public IntellijJdkBuildOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
            settings.setIntelliJdkDirectory(new File(value));
        }
    }
}
