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

import org.gradle.StartParameter;
import org.gradle.internal.service.ServiceRegistry;

/**
 * <p>A {@code GradleLauncherFactory} is responsible for creating a {@link GradleLauncher} instance for a build, from a {@link
 * org.gradle.StartParameter}.</p>
 *
 * Caller must call {@link GradleLauncher#stop()} when finished with the launcher.
 */
public interface GradleLauncherFactory {
    /**
     * Creates a new {@link GradleLauncher} instance for a new build request.
     * Fails if a build is in progress.
     *
     * @param startParameter The settings for the build.
     * @param requestContext The context in which the build is running.
     * @param parent The parent service registry for this build.
     */
    GradleLauncher newInstance(StartParameter startParameter, BuildRequestContext requestContext, ServiceRegistry parent);
}
