/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.buildconfiguration.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.platform.BuildPlatform;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task that generates or updates the Gradle Daemon JVM criteria.
 * This controls which JVM version and vendor are required to run the Gradle Daemon.
 *
 * @since 8.8
 */
@DisableCachingByDefault(because = "Not worth caching")
@Incubating
public abstract class UpdateDaemonJvm extends DefaultTask {

    public static final ProblemId TASK_CONFIGURATION_PROBLEM_ID =
        ProblemId.create("task-configuration", "Invalid task configuration",
            GradleCoreProblemGroup.daemonToolchain().configurationGeneration());

    private final DaemonJvmPropertiesModifier daemonJvmPropertiesModifier;

    @Inject
    public UpdateDaemonJvm(DaemonJvmPropertiesModifier daemonJvmPropertiesModifier) {
        this.daemonJvmPropertiesModifier = daemonJvmPropertiesModifier;
    }

    @TaskAction
    void generate() {
        IncubationLogger.incubatingFeatureUsed("Daemon JVM criteria");

        String vendorCriteria = getVendor()
            .map(v -> ((DefaultJvmVendorSpec) v).toCriteria())
            .orElse(null);

        daemonJvmPropertiesModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            getLanguageVersion().get(),
            vendorCriteria,
            getNativeImageCapable().getOrElse(false),
            getToolchainDownloadUrls().get()
        );
    }

    /**
     * Output file for daemon JVM properties.
     *
     * @since 8.8
     */
    @OutputFile
    @Incubating
    public abstract RegularFileProperty getPropertiesFile();

    /**
     * Required JVM language version.
     *
     * @since 8.13
     */
    @Input
    @Optional
    @Option(option = "jvm-version", description = "The version of the JVM required to run the Gradle Daemon.")
    @Incubating
    public abstract Property<JavaLanguageVersion> getLanguageVersion();

    /**
     * Required JVM vendor.
     *
     * @since 8.13
     */
    @Input
    @Optional
    @Option(option = "jvm-vendor", description = "The vendor of the JVM required to run the Gradle Daemon.")
    @Incubating
    public abstract Property<JvmVendorSpec> getVendor();

    /**
     * List of supported JVM vendors.
     *
     * @since 8.10
     */
    @OptionValues("jvm-vendor")
    public List<String> getAvailableVendors() {
        return Arrays.stream(JvmVendor.KnownJvmVendor.values())
            .filter(v -> v != JvmVendor.KnownJvmVendor.UNKNOWN)
            .map(Enum::name)
            .collect(Collectors.toList());
    }

    /**
     * Whether native-image capability is required.
     *
     * @since 8.14
     */
    @Input
    @Optional
    @Option(option = "native-image-capable", description = "Indicates if the native-image capability is required.")
    @Incubating
    public abstract Property<Boolean> getNativeImageCapable();

    /**
     * Platforms for which to generate toolchain download URLs.
     *
     * @since 8.13
     */
    @Internal
    @Incubating
    public abstract SetProperty<BuildPlatform> getToolchainPlatforms();

    /**
     * Toolchain download URLs per platform.
     *
     * @since 8.13
     */
    @Input
    @Incubating
    public abstract MapProperty<BuildPlatform, URI> getToolchainDownloadUrls();
}
