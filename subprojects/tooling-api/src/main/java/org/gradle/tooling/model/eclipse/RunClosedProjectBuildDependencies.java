/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.tooling.model.eclipse;

import org.gradle.api.Incubating;

/**
 * A tooling model that instructs Gradle to run tasks to build artifacts for closed projects.
 *
 * Similarly to {@link RunEclipseSynchronizationTasks}, this is a special tooling model as it does
 * not provide any information. However, when requested, Gradle will build the artifacts required
 * to substitute the closed gradle projects in the eclipse workspace.
 *
 * This is a parameterized model and requires an {@link EclipseRuntime} parameter to calculate the
 * closed projects.
 *
 * @since 5.6
 */
@Incubating
public interface RunClosedProjectBuildDependencies {
}
