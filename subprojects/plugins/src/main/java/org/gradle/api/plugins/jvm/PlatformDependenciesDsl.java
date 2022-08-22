/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.plugins.jvm;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderConvertible;

import javax.annotation.Nullable;

/**
 * Dependency APIs for using <a href="https://docs.gradle.org/current/userguide/java_platform_plugin.html#java_platform_plugin">Platforms</a> in {@code dependencies} blocks.
 *
 * @since 7.6
 */
@Incubating
public interface PlatformDependenciesDsl extends DependenciesDsl {

    default ExternalModuleDependency platform(CharSequence dependencyNotation) {
        return platform(getDependencyFactory().create(dependencyNotation));
    }

    default ExternalModuleDependency platform(@Nullable String group, String name, @Nullable String version) {
        return platform(getDependencyFactory().create(group, name, version));
    }

    <D extends ModuleDependency> D platform(D dependency);

    default Provider<? extends MinimalExternalModuleDependency> platform(ProviderConvertible<? extends MinimalExternalModuleDependency> dependency) {
        return dependency.asProvider().map(this::platform);
    }

    default Provider<? extends ExternalModuleDependency> platform(Provider<? extends ExternalModuleDependency> dependency) {
        return dependency.map(this::platform);
    }

    default ExternalModuleDependency enforcedPlatform(CharSequence dependencyNotation) {
        return enforcedPlatform(getDependencyFactory().create(dependencyNotation));
    }

    default ExternalModuleDependency enforcedPlatform(@Nullable String group, String name, @Nullable String version) {
        return enforcedPlatform(getDependencyFactory().create(group, name, version));
    }

    <D extends ModuleDependency> D enforcedPlatform(D dependency);

    <D extends ExternalDependency> D enforcedPlatform(D dependency);

    default Provider<? extends MinimalExternalModuleDependency> enforcedPlatform(ProviderConvertible<? extends MinimalExternalModuleDependency> dependency) {
        return dependency.asProvider().map(this::enforcedPlatform);
    }

    default Provider<? extends ExternalModuleDependency> enforcedPlatform(Provider<? extends ExternalModuleDependency> dependency) {
        return dependency.map(this::enforcedPlatform);
    }
}
