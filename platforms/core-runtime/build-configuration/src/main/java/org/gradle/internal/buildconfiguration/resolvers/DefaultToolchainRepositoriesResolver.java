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

package org.gradle.internal.buildconfiguration.resolvers;

import org.gradle.api.model.ObjectFactory;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainDownload;
import org.gradle.jvm.toolchain.JavaToolchainRequest;
import org.gradle.jvm.toolchain.JavaToolchainResolverRegistry;
import org.gradle.jvm.toolchain.JavaToolchainSpec;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.jvm.toolchain.JvmVendorSpec;
import org.gradle.jvm.toolchain.internal.DefaultJavaToolchainRequest;
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec;
import org.gradle.jvm.toolchain.internal.JavaToolchainResolverRegistryInternal;
import org.gradle.jvm.toolchain.internal.RealizedJavaToolchainRepository;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.gradle.internal.buildconfiguration.resolvers.ToolchainSupportedPlatformsMatrix.getToolchainSupportedBuildPlatforms;

public class DefaultToolchainRepositoriesResolver implements ToolchainRepositoriesResolver {

    private final JavaToolchainResolverRegistryInternal toolchainResolverRegistry;
    private final ObjectFactory objectFactory;

    public DefaultToolchainRepositoriesResolver(JavaToolchainResolverRegistry toolchainResolverRegistry, ObjectFactory objectFactory) {
        this.toolchainResolverRegistry = (JavaToolchainResolverRegistryInternal) toolchainResolverRegistry;
        this.objectFactory = objectFactory;
    }

    @Override
    public Map<BuildPlatform, Optional<URI>> resolveToolchainDownloadUrlsByPlatform(
        JavaLanguageVersion toolchainVersion,
        @Nullable String toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) throws UnconfiguredToolchainRepositoriesResolver {
        List<? extends RealizedJavaToolchainRepository> toolchainRepositories = toolchainResolverRegistry.requestedRepositories();
        if (toolchainRepositories.isEmpty()) {
            throw new UnconfiguredToolchainRepositoriesResolver();
        }

        JavaToolchainSpec toolchainSpec = createToolchainSpec(toolchainVersion, toolchainVendor, toolchainImplementation);
        Map<BuildPlatform, Optional<URI>> toolchainDownloadUrlByPlatformMap = new HashMap<>();

        getToolchainSupportedBuildPlatforms().stream()
            .map(buildPlatform -> new DefaultJavaToolchainRequest(toolchainSpec, buildPlatform))
            .forEach(javaToolchainRequest -> {
                Optional<URI> downloadUrl = resolveToolchainDownloadUrlRequest(toolchainRepositories, javaToolchainRequest);
                toolchainDownloadUrlByPlatformMap.put(javaToolchainRequest.getBuildPlatform(), downloadUrl);
            });

        return toolchainDownloadUrlByPlatformMap;
    }

    private Optional<URI> resolveToolchainDownloadUrlRequest(List<? extends RealizedJavaToolchainRepository> toolchainRepositories, JavaToolchainRequest javaToolchainRequest) {
        for (RealizedJavaToolchainRepository repository : toolchainRepositories) {
            Optional<JavaToolchainDownload> javaToolchainDownload = repository.getResolver().resolve(javaToolchainRequest);
            if (javaToolchainDownload.isPresent()) {
                return Optional.of(javaToolchainDownload.get().getUri());
            }
        }
        return Optional.empty();
    }

    private JavaToolchainSpec createToolchainSpec(
        JavaLanguageVersion toolchainVersion,
        @Nullable String toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) {
        DefaultToolchainSpec toolchainSpec = objectFactory.newInstance(DefaultToolchainSpec.class);
        toolchainSpec.getLanguageVersion().set(toolchainVersion);
        if (toolchainVendor != null) {
            toolchainSpec.getVendor().set(JvmVendorSpec.matching(toolchainVendor));
        }
        if (toolchainImplementation != null) {
            toolchainSpec.getImplementation().set(toolchainImplementation);
        }

        return toolchainSpec;
    }
}
