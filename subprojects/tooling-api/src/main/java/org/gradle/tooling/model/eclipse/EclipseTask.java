/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.tooling.model.Task;

/**
 * Deprecated because Gradle tasks are not associated with Eclipse projects.
 *
 * @deprecated Use {@link EclipseProject#getGradleProject()} to determine the associated Gradle project for an Eclipse project,
 * then use {@link org.gradle.tooling.model.GradleProject#getTasks()} to determine the tasks for the Gradle project.
 */
@Deprecated
public interface EclipseTask extends Task {
    /**
     * {@inheritDoc}
     */
    EclipseProject getProject();
}
