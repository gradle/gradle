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
import org.gradle.buildconfiguration.tasks.UpdateDaemonJvm;
import org.gradle.configuration.project.ProjectConfigureAction;
import org.gradle.internal.Pair;
import org.gradle.internal.jvm.Jvm;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
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
            project.getTasks().register(TASK_NAME, UpdateDaemonJvm.class, task -> {
                task.setGroup("Build Setup");
                task.setDescription("Generates or updates the Gradle Daemon JVM criteria.");
                task.getPropertiesFile().convention(project.getLayout().getProjectDirectory().file(DaemonJvmPropertiesDefaults.DAEMON_JVM_PROPERTIES_FILE));
                task.getJvmVersion().convention(JavaLanguageVersion.of(Jvm.current().getJavaVersionMajor()));
                task.getToolchainPlatforms().convention(
                    Stream.of(Architecture.X86_64, Architecture.AARCH64).flatMap(arch ->
                            Stream.of(OperatingSystem.values()).map(os -> BuildPlatformFactory.of(arch, os)))
                        .collect(Collectors.toSet()));
                task.getToolchainDownloadUrls().convention(task.getToolchainPlatforms()
                    .zip(task.getJvmVersion().zip(task.getJvmVendor().orElse("any"), Pair::of),
                        (platforms, versionVendor) -> {
                            String vendor = versionVendor.getRight();
                            JavaToolchainSpec toolchainSpec = project.getObjects().newInstance(DefaultToolchainSpec.class);
                            toolchainSpec.getLanguageVersion().set(versionVendor.getLeft());
                            if (!vendor.equals("any")) {
                                toolchainSpec.getVendor().set(JvmVendorSpec.of(vendor));
                            }
                            JavaToolchainResolverService resolverService = project.getServices().get(JavaToolchainResolverService.class);
                            if (!platforms.isEmpty() && !resolverService.hasConfiguredToolchainRepositories()) {
                                // TODO revisit failure condition, but don't require generation as long as its results are not used
//                                throw new UnconfiguredToolchainRepositoriesResolver();
                                return emptyMap();
                            }
                            Map<BuildPlatform, Optional<URI>> buildPlatformOptionalUriMap = platforms.stream()
                                .collect(Collectors.toMap(platform -> platform,
                                    platform -> resolverService.tryResolve(new DefaultJavaToolchainRequest(toolchainSpec, platform)).map(JavaToolchainDownload::getUri)));
                            return buildPlatformOptionalUriMap.entrySet().stream()
                                .filter(e -> e.getValue().isPresent())
                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get()));
                        }));
            });
        }
    }
}
