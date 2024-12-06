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
import org.gradle.api.Incubating;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import org.gradle.internal.Pair;
import org.gradle.internal.buildconfiguration.DaemonJvmPropertiesDefaults;
import org.gradle.internal.buildconfiguration.resolvers.UnconfiguredToolchainRepositoriesResolver;
import org.gradle.internal.buildconfiguration.tasks.DaemonJvmPropertiesModifier;
import org.gradle.internal.jvm.inspection.JvmVendor;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainResolverService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.OperatingSystem;
import org.gradle.platform.internal.DefaultBuildPlatform;
import org.gradle.util.internal.IncubationLogger;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Generates or updates the Gradle Daemon JVM criteria.
 *
 * This controls the version of the JVM required to run the Gradle Daemon.
 *
 * @since 8.8
 */
@DisableCachingByDefault(because = "Not worth caching")
@Incubating
public abstract class UpdateDaemonJvm extends DefaultTask {

    private final DaemonJvmPropertiesModifier daemonJvmPropertiesModifier;

    /**
     * Constructor.
     *
     * @since 8.8
     */
    @Inject
    public UpdateDaemonJvm(DaemonJvmPropertiesModifier daemonJvmPropertiesModifier, JavaToolchainResolverService javaToolchainResolverService) {
        this.daemonJvmPropertiesModifier = daemonJvmPropertiesModifier;
        getToolchainPlatforms().convention(
            Stream.of(Architecture.X86_64, Architecture.AARCH64).flatMap(arch ->
                Stream.of(OperatingSystem.values()).map(os -> new DefaultBuildPlatform(arch, os)))
                .collect(Collectors.toSet()));
        getToolchainDownloadUrls().convention(getToolchainPlatforms()
            .zip(getJvmVersion().zip(getJvmVendor().orElse("any"), Pair::of),
                (platforms, versionVendor) -> {
                    String vendor = versionVendor.getRight();
                    JavaToolchainSpec toolchainSpec = getProject().getObjects().newInstance(DefaultToolchainSpec.class);
                    toolchainSpec.getLanguageVersion().set(versionVendor.getLeft());
                    if (!vendor.equals("any")) {
                        toolchainSpec.getVendor().set(JvmVendorSpec.of(vendor));
                    }
                    if (!javaToolchainResolverService.hasConfiguredToolchainRepositories()) {
                        throw new UnconfiguredToolchainRepositoriesResolver();
                    }
                    Map<BuildPlatform, java.util.Optional<URI>> buildPlatformOptionalUriMap = platforms.stream()
                        .collect(Collectors.toMap(platform -> platform,
                            platform -> javaToolchainResolverService.tryResolve(new DefaultJavaToolchainRequest(toolchainSpec, platform)).map(JavaToolchainDownload::getUri)));
                    return buildPlatformOptionalUriMap.entrySet().stream()
                        .filter(e -> e.getValue().isPresent())
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
                }));
    }

    @TaskAction
    void generate() {
        IncubationLogger.incubatingFeatureUsed("Daemon JVM criteria");

        final String jvmVendor;
        Provider<JvmVendorSpec> vendorSpec = getJvmVendor().map(JvmVendorSpec::of);
        if (vendorSpec.isPresent()) {
            // TODO change this to something else, we should serialize the spec, not just the vendor string
            jvmVendor = getJvmVendor().get();
        } else {
            jvmVendor = null; // any vendor is acceptable
        }
        daemonJvmPropertiesModifier.updateJvmCriteria(
            getPropertiesFile().get().getAsFile(),
            getJvmVersion().get(),
            jvmVendor,
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
    @Incubating
    public abstract RegularFileProperty getPropertiesFile();

    /**
     * The version of the JVM required to run the Gradle Daemon.
     *
     * @since 8.8
     */
    @Input
    @Optional
    @Option(option = "jvm-version", description = "The version of the JVM required to run the Gradle Daemon.")
    @Incubating
    public abstract Property<JavaLanguageVersion> getJvmVersion();

    /**
     * The vendor of Java required to run the Gradle Daemon.
     * <p>
     * When unset, any vendor is acceptable.
     * </p>
     *
     * @since 8.10
     */
    @Input
    @Optional
    @Incubating
    @Option(option = "jvm-vendor", description = "The vendor of the JVM required to run the Gradle Daemon.")
    public abstract Property<String> getJvmVendor();

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
     * The set of {@link BuildPlatform} for which download links should be generated.
     * <p>
     * By convention Gradle sources those from the combination of all supported {@link org.gradle.platform.OperatingSystem}
     * and the following architectures: {@link org.gradle.platform.Architecture#X86_64} and {@link org.gradle.platform.Architecture#AARCH64}.
     *
     * @since 8.12
     */
    @Internal
    @Incubating
    public abstract SetProperty<BuildPlatform> getToolchainPlatforms();

    /**
     * The download URLs for the toolchains for the given platforms.
     * <p>
     * By convention, Gradle will combine the {@link #getToolchainPlatforms() build platforms}, {@link #getJvmVersion() JVM version} and {@link #getJvmVendor()}
     * to resolve download URLs using the configured {@link org.gradle.jvm.toolchain.JavaToolchainRepository Java toolchain repositories}.
     * <p>
     * If the convention applies and no toolchain repositories are defined, an exception will be thrown.
     *
     * @since 8.12
     */
    @Input
    @Incubating
    public abstract MapProperty<BuildPlatform, URI> getToolchainDownloadUrls();
}
