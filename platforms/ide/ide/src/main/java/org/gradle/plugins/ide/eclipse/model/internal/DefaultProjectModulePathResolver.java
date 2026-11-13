/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal;

import org.gradle.api.Project;
import org.jspecify.annotations.NullMarked;

/**
 * Reads the flag straight off the given project.
 *
 * <p>This is the behaviour the {@code eclipse} tasks have always had. It reads the task container of
 * the project it is given, so it reports a cross-project access when that is a project other than
 * the one being configured and Isolated Projects is enabled. Model builders that have to be
 * Isolated Projects safe use {@link PrecomputedProjectModulePathResolver} instead.
 */
@NullMarked
public class DefaultProjectModulePathResolver implements ProjectModulePathResolver {

    @Override
    public boolean isInferModulePath(Project project) {
        return EclipseClassPathUtil.isInferModulePath(project);
    }
}
