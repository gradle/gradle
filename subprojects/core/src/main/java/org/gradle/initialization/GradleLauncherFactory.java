/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.api.internal.BuildDefinition;
import org.gradle.internal.build.RootBuildState;
import org.gradle.internal.buildtree.BuildTreeState;

/**
 * <p>A {@code GradleLauncherFactory} is responsible for creating a {@link GradleLauncher} instance for a root build.
 *
 * Caller must call {@link GradleLauncher#stop()} when finished with the launcher.
 *
 * Note: you should be using {@link org.gradle.internal.build.BuildStateRegistry} instead of this interface to create builds.
 */
public interface GradleLauncherFactory {
    GradleLauncher newInstance(BuildDefinition buildDefinition, RootBuildState build, BuildTreeState owner);
}
