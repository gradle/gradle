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
package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.plugins.ide.eclipse.EclipsePlugin;
import org.gradle.plugins.ide.eclipse.model.internal.EclipseClassPathUtil;
import org.gradle.plugins.ide.internal.tooling.eclipse.IsolatedEclipseProjectInternal;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * Builds {@link IsolatedEclipseProjectInternal} for a single project.
 *
 * <p>Everything this reads belongs to the project it is given, so it is safe to run under that
 * project's own lock. The builder of the whole {@code EclipseProject} model requests this model for
 * every project instead of reaching into them itself.
 */
@NullMarked
public class IsolatedEclipseProjectBuilder implements ToolingModelBuilder {

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(IsolatedEclipseProjectInternal.class.getName());
    }

    @Override
    public IsolatedEclipseProjectInternal buildAll(String modelName, Project project) {
        // The classpath data below is only populated once the plugin has contributed its conventions.
        project.getPluginManager().apply(EclipsePlugin.class);
        return new IsolatedEclipseProjectInternal(EclipseClassPathUtil.isInferModulePath(project));
    }
}
