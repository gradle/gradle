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
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.util.Path;
import org.jspecify.annotations.NullMarked;

import java.util.Map;

/**
 * Answers from values gathered per project before classpath resolution started.
 *
 * <p>Projects are keyed by build tree path, which is unique across the whole build tree, so this also
 * covers project dependencies substituted from included builds. Only the project identity is read
 * from the given project, which is metadata rather than mutable state and therefore does not report a
 * cross-project access.
 *
 * <p>A project that is missing from the map is treated as not being a Java module. That is the same
 * answer {@link EclipseClassPathUtil} gives for a project without a {@code compileJava} task, and it
 * keeps a gap in the gathered values from failing the build.
 */
@NullMarked
public class PrecomputedProjectModulePathResolver implements ProjectModulePathResolver {

    private final Map<Path, Boolean> inferModulePathByBuildTreePath;

    public PrecomputedProjectModulePathResolver(Map<Path, Boolean> inferModulePathByBuildTreePath) {
        this.inferModulePathByBuildTreePath = inferModulePathByBuildTreePath;
    }

    @Override
    public boolean isInferModulePath(Project project) {
        Path buildTreePath = ((ProjectInternal) project).getProjectIdentity().getBuildTreePath();
        return inferModulePathByBuildTreePath.getOrDefault(buildTreePath, false);
    }
}
