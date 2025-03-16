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

package org.gradle.internal.buildconfiguration;

import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm;
import org.gradle.configuration.project.ProjectConfigureAction;
import org.gradle.internal.Pair;
import org.gradle.internal.buildconfiguration.resolvers.UnconfiguredToolchainRepositoriesResolver;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.internal.DefaultJvmVendorSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverService;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.platform.Architecture;
import org.gradle.platform.BuildPlatform;
import org.gradle.platform.BuildPlatformFactory;
import org.gradle.platform.OperatingSystem;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;

public class DaemonJvmPropertiesConfigurator implements ProjectConfigureAction {

    public static final String TASK_NAME = "updateDaemonJvm";

    @Override
    public void execute(ProjectInternal project) {
        // Only useful for the root project
        if (project.getParent() == null) {
            ProblemReporter reporter = project.getServices().get(Problems.class).getReporter();
            project.getTasks().register(TASK_NAME, UpdateDaemonJvm.class, task -> {
                task.setGroup("Build Setup");
                task.setDescription("Generates or updates the Gradle Daemon JVM criteria.");
                task.getPropertiesFile().convention(project.getLayout().getProjectDirectory().file(DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE));
                task.getLanguageVersion().convention(JavaLanguageVersion.of(Jvm.current().getJavaVersionMajor()));
                task.getToolchainPlatforms().convention(
                    Stream.of(Architecture.X86_64, Architecture.AARCH64).flatMap(arch ->
                            Stream.of(OperatingSystem.values()).map(os -> BuildPlatformFactory.of(arch, os)))
                        .collect(Collectors.toSet()));
                task.getToolchainDownloadUrls().convention(task.getToolchainPlatforms()
                    .zip(task.getLanguageVersion().zip(task.getVendor().orElse(DefaultJvmVendorSpec.any()), Pair::of),
                        (platforms, versionVendor) -> {
                            JvmVendorSpec vendor = versionVendor.getRight();
                            JavaToolchainSpec toolchainSpec = project.getObjects().newInstance(DefaultToolchainSpec.class);
                            toolchainSpec.getLanguageVersion().set(versionVendor.getLeft());
                            if (!vendor.equals(DefaultJvmVendorSpec.any())) {
                                toolchainSpec.getVendor().set(vendor);
                            }
                            if (platforms.isEmpty()) {
                                return emptyMap();
                            }

                            JavaToolchainResolverService resolverService = project.getServices().get(JavaToolchainResolverService.class);
                            if (!resolverService.hasConfiguredToolchainRepositories()) {
                                UnconfiguredToolchainRepositoriesResolver exception = new UnconfiguredToolchainRepositoriesResolver();
                                throw reporter.throwing(exception, UpdateDaemonJvm.TASK_CONFIGURATION_PROBLEM_ID,
                                    problemSpec -> {
                                        problemSpec.contextualLabel(exception.getLocalizedMessage());
                                        exception.getResolutions().forEach(problemSpec::solution);
                                    });
                            }
                            Map<BuildPlatform, Optional<URI>> buildPlatformOptionalUriMap = platforms.stream()
                                .collect(Collectors.toMap(platform -> platform,
                                    platform -> resolverService.tryResolve(new DefaultJavaToolchainRequest(toolchainSpec, platform)).map(JavaToolchainDownload::getUri)));
                            Map<BuildPlatform, URI> platformToDownloadUri = buildPlatformOptionalUriMap.entrySet().stream()
                                .filter(e -> e.getValue().isPresent())
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
                            if (platformToDownloadUri.isEmpty()) {
                                String message = "Toolchain resolvers did not return download URLs providing a JDK matching " + toolchainSpec + " for any of the requested platforms " + platforms;
                                throw reporter.throwing(new IllegalStateException(message), UpdateDaemonJvm.TASK_CONFIGURATION_PROBLEM_ID,
                                    problemSpec -> {
                                        problemSpec.contextualLabel(message);
                                        problemSpec.solution("Use a toolchain download repository capable of resolving the toolchain spec for the given platforms");
                                        problemSpec.documentedAt(Documentation.userManual("gradle_daemon", "sec:daemon_jvm_provisioning").getUrl());
                                    });
                            }
                            return platformToDownloadUri;
                        }));
            });
        }
    }
}
