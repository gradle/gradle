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

package org.gradle.buildconfiguration.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.platform.BuildPlatform;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates or updates the Gradle Daemon JVM criteria.
 *
 * This controls the version of the JVM required to run the Gradle Daemon.
 *
 * @since 8.8
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class UpdateDaemonJvm extends DefaultTask {

    /**
     * The problem id for task configuration problems.
     *
     * @since 8.13
     */
    public static final ProblemId TASK_CONFIGURATION_PROBLEM_ID = ProblemId.create("task-configuration", "Invalid task configuration", GradleCoreProblemGroup.daemonToolchain().configurationGeneration());

    private final DaemonJvmPropertiesModifier daemonJvmPropertiesModifier;

    /**
     * Constructor.
     *
     * @since 8.8
     */
    @Inject
    public UpdateDaemonJvm(DaemonJvmPropertiesModifier daemonJvmPropertiesModifier) {
        this.daemonJvmPropertiesModifier = daemonJvmPropertiesModifier;
    }

    @TaskAction
    void generate() {
        final String jvmVendorCriteria;
        if (getVendor().isPresent()) {
            jvmVendorCriteria = getVendor().map(v -> ((DefaultJvmVendorSpec)v).toCriteria()).get();
        } else {
            jvmVendorCriteria = null; // any vendor is acceptable
        }
        daemonJvmPropertiesModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            getLanguageVersion().get(),
            jvmVendorCriteria,
            getNativeImageCapable().getOrElse(false),
            getToolchainDownloadUrls().get()
        );
    }

    /**
     * The file to write the requested daemon JVM criteria to.
     *
     * {@value DaemonJvmPropertiesDefaults#DAEMON_JVM_PROPERTIES_FILE}
     *
     * @since 8.8
     */
    @OutputFile
    public abstract RegularFileProperty getPropertiesFile();

    /**
     * The version of the JVM required to run the Gradle Daemon.
     * <p>
     * By convention, for the task created on the root project, Gradle will use the JVM version of the current JVM.
     *
     * @since 8.13
     */
    @Input
    @Optional
    @Option(option = "jvm-version", description = "The version of the JVM required to run the Gradle Daemon.")
    public abstract Property<JavaLanguageVersion> getLanguageVersion();

    /**
     * Configures the vendor spec for the daemon toolchain properties generation.
     *
     * @since 8.13
     */
    @Input
    @Optional
    @Option(option = "jvm-vendor", description = "The vendor of the JVM required to run the Gradle Daemon.")
    public abstract Property<JvmVendorSpec> getVendor();

    /**
     * Returns the supported JVM vendors.
     *
     * @return supported JVM vendors
     * @since 8.10
     */
    @OptionValues("jvm-vendor")
    public List<String> getAvailableVendors() {
        return Arrays.stream(JvmVendor.KnownJvmVendor.values()).filter(e -> e!=JvmVendor.KnownJvmVendor.UNKNOWN).map(Enum::name).collect(Collectors.toList());
    }

    /**
     * Indicates it the native-image capability is required.
     *
     * @since 8.14
     */
    @Input
    @Optional
    @Option(option = "native-image-capable", description = "Indicates if the native-image capability is required.")
    public abstract Property<Boolean> getNativeImageCapable();

    /**
     * The set of {@link BuildPlatform} for which download links should be generated.
     * <p>
     * By convention, for the task created on the root project, Gradle sources those from the combination of all supported {@link org.gradle.platform.OperatingSystem}
     * and the following architectures: {@link org.gradle.platform.Architecture#X86_64} and {@link org.gradle.platform.Architecture#AARCH64}.
     *
     * @since 8.13
     */
    @Internal
    public abstract SetProperty<BuildPlatform> getToolchainPlatforms();

    /**
     * The download URLs for the toolchains for the given platforms.
     * <p>
     * By convention, for the task created on the root project, Gradle will combine the {@link #getToolchainPlatforms() build platforms}, {@link #getLanguageVersion() JVM version} and {@link #getVendor()}
     * to resolve download URLs using the configured {@link org.gradle.jvm.toolchain.JavaToolchainRepository Java toolchain repositories}.
     * <p>
     * If the convention applies and no toolchain repositories are defined, an exception will be thrown.
     *
     * @since 8.13
     */
    @Input
    public abstract MapProperty<BuildPlatform, URI> getToolchainDownloadUrls();
}
