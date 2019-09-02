/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.internal.GradleInternal;

/**
 * Responsible for creating and configuring the projects of a `Gradle` instance. The result is passed to a {@link org.gradle.initialization.TaskExecutionPreparer} to prepare for task execution. Prior to project preparation, the `Gradle` instance has its settings object configured by a {@link org.gradle.initialization.SettingsPreparer}.
 *
 * <p>This stage includes running the build script for each project.</p>
 */
public interface ProjectsPreparer {
    void prepareProjects(GradleInternal gradle);
}
