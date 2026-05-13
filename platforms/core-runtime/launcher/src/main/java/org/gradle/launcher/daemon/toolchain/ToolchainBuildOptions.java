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

import org.gradle.cli.OptionCategory;
import org.gradle.internal.buildoption.AbstractBuildOption;
import org.gradle.internal.buildoption.BooleanBuildOption;
import org.gradle.internal.buildoption.BuildOption;
import org.gradle.internal.buildoption.BuildOptionSet;
import org.gradle.internal.buildoption.Origin;
import org.gradle.internal.buildoption.StringBuildOption;
import org.gradle.jvm.toolchain.internal.AutoInstalledInstallationSupplier;
import org.gradle.jvm.toolchain.internal.EnvironmentVariableListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.IntellijInstallationSupplier;
import org.gradle.jvm.toolchain.internal.LocationListInstallationSupplier;
import org.gradle.jvm.toolchain.internal.ToolchainConfiguration;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ToolchainBuildOptions {
    public static BuildOptionSet<ToolchainConfiguration> forToolChainConfiguration() {
        return new BuildOptionSet<ToolchainConfiguration>() {
            private final List<? extends BuildOption<? super ToolchainConfiguration>> options = Arrays.asList(
                new JavaInstallationPathsOption<ToolchainConfiguration>() {
                    @Override
                    public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
                        settings.setInstallationsFromPaths(Arrays.asList(value.split(",")));
                    }
                },
                new JavaInstallationEnvironmentPathsOption<ToolchainConfiguration>() {
                    @Override
                    public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
                        settings.setJavaInstallationsFromEnvironment(Arrays.asList(value.split(",")));
                    }
                },
                new AutoDetectionOption<ToolchainConfiguration>() {
                    @Override
                    public void applyTo(boolean value, ToolchainConfiguration settings, Origin origin) {
                        settings.setAutoDetectEnabled(value);
                    }
                },
                new AutoDownloadOption<ToolchainConfiguration>() {
                    @Override
                    public void applyTo(boolean value, ToolchainConfiguration settings, Origin origin) {
                        settings.setDownloadEnabled(value);
                    }
                },
                new IntellijJdkBuildOption<ToolchainConfiguration>() {
                    @Override
                    public void applyTo(String value, ToolchainConfiguration settings, Origin origin) {
                        settings.setIntelliJdkDirectory(new File(value));
                    }
                }
            );

            @Override
            public List<? extends BuildOption<? super ToolchainConfiguration>> getAllOptions() {
                return options;
            }
        };
    }

    public static BuildOptionSet<Map<String, String>> forProjectProperties() {
        return new BuildOptionSet<Map<String, String>>() {
            private final List<? extends BuildOption<? super Map<String, String>>> options = Arrays.asList(
                new JavaInstallationPathsOption<Map<String, String>>() {
                    @Override
                    public void applyTo(String value, Map<String, String> properties, Origin origin) {
                        properties.putIfAbsent(getPropertyName(this), value);
                    }
                },
                new JavaInstallationEnvironmentPathsOption<Map<String, String>>() {
                    @Override
                    public void applyTo(String value, Map<String, String> properties, Origin origin) {
                        properties.putIfAbsent(getPropertyName(this), value);
                    }
                },
                new AutoDetectionOption<Map<String, String>>() {
                    @Override
                    public void applyTo(boolean value, Map<String, String> properties, Origin origin) {
                        properties.putIfAbsent(getPropertyName(this), Boolean.toString(value));
                    }
                },
                new AutoDownloadOption<Map<String, String>>() {
                    @Override
                    public void applyTo(boolean value, Map<String, String> properties, Origin origin) {
                        properties.putIfAbsent(getPropertyName(this), Boolean.toString(value));
                    }
                },
                new IntellijJdkBuildOption<Map<String, String>>() {
                    @Override
                    public void applyTo(String value, Map<String, String> properties, Origin origin) {
                        properties.putIfAbsent(getPropertyName(this), value);
                    }
                }
            );

            @Override
            public List<? extends BuildOption<? super Map<String, String>>> getAllOptions() {
                return options;
            }
        };
    }

    private static String getPropertyName(AbstractBuildOption<?, ?> option) {
        String property = option.getProperty();
        assert property != null : "Toolchain build option must have a property name";
        return property;
    }

    private abstract static class JavaInstallationPathsOption<T> extends StringBuildOption<T> {
        private static final String GRADLE_PROPERTY = LocationListInstallationSupplier.JAVA_INSTALLATIONS_PATHS_PROPERTY;

        public JavaInstallationPathsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    private abstract static class JavaInstallationEnvironmentPathsOption<T> extends StringBuildOption<T> {
        private static final String GRADLE_PROPERTY = EnvironmentVariableListInstallationSupplier.JAVA_INSTALLATIONS_FROM_ENV_PROPERTY;

        public JavaInstallationEnvironmentPathsOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    private abstract static class AutoDetectionOption<T> extends BooleanBuildOption<T> {
        private static final String GRADLE_PROPERTY = ToolchainConfiguration.AUTO_DETECT;

        public AutoDetectionOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    private abstract static class AutoDownloadOption<T> extends BooleanBuildOption<T> {
        private static final String GRADLE_PROPERTY = AutoInstalledInstallationSupplier.AUTO_DOWNLOAD;

        public AutoDownloadOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }

    private abstract static class IntellijJdkBuildOption<T> extends StringBuildOption<T> {
        private static final String GRADLE_PROPERTY = IntellijInstallationSupplier.INTELLIJ_JDK_DIR_PROPERTY;

        public IntellijJdkBuildOption() {
            super(GRADLE_PROPERTY);
        }

        @Override
        protected OptionCategory getCategory() {
            return OptionCategory.CONFIGURATION;
        }
    }
}
