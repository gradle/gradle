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

import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JvmImplementation;
import org.gradle.platform.BuildPlatform;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Map;
import java.util.Optional;

public interface ToolchainRepositoriesResolver {

    Map<BuildPlatform, Optional<URI>> resolveToolchainDownloadUrlsByPlatform(
        JavaLanguageVersion toolchainVersion,
        @Nullable String toolchainVendor,
        @Nullable JvmImplementation toolchainImplementation
    ) throws UnconfiguredToolchainRepositoriesResolver;
}
