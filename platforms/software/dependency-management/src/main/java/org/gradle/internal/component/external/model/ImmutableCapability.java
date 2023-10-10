/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.component.external.model;

import javax.annotation.Nullable;

/**
 * This is a deprecated internal class that exists specifically to be used by the
 * Android and Kotlin Android plugins, which expect this type.
 * <p>
 * Be careful to not confuse it with the {@link org.gradle.api.internal.capabilities.ImmutableCapability ImmutableCapability}
 * interface.
 * <p>
 * Without this class in place these smoke tests fail:
 * <ul>
 * <li>{@code KotlinPluginAndroidKotlinDSLSmokeTest} and {@code KotlinPluginAndroidGroovyDSLSmokeTest} with kotlin 1.6.21, agp 7.3.1 - 8.0.0-alpha11.</li>
 * <li>{@code AndroidPluginsSmokeTest} with 7.3.1 - 8.0.0-alpha11.</li>
 * <li>{@code AndroidSantaTrackerIncrementalCompilationSmokeTest}, {@code AndroidSantaTrackerCachingCompilationSmokeTest}, {@code AndroidSantaTrackerDeprecationSmokeTest} with the same versions.</li>
 * </ul>
 * They all fail with some version (different configurations and task names) of:
 *
 * <code>
 *     Could not resolve all dependencies for configuration ':cityquiz:debugRuntimeClasspath'.
 *       Could not create task ':santa-tracker:generateDebugFeatureTransitiveDeps'.
 *         org/gradle/internal/component/external/model/ImmutableCapability
 * </code>
 *
 * The task at fault is {@code com.android.build.gradle.internal.tasks.featuresplit.PackagedDependenciesWriterTask`}.
 *
 * @deprecated Use {@link DefaultImmutableCapability} instead.
 */
@Deprecated
public class ImmutableCapability extends DefaultImmutableCapability {
    public ImmutableCapability(String group, String name, @Nullable String version) {
        super(group, name, version);
    }
}
