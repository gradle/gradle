/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.build;

import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultProjectComponentIdentifier;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.IncludedBuildSpec;
import org.gradle.util.Path;

public abstract class AbstractBuildState implements BuildState {
    @Override
    public String toString() {
        return getBuildIdentifier().toString();
    }

    @Override
    public void assertCanAdd(IncludedBuildSpec includedBuildSpec) {
        throw new UnsupportedOperationException("Cannot include build '" + includedBuildSpec.rootDir.getName() + "' in " + getBuildIdentifier() + ". This is not supported yet.");
    }

    @Override
    public ProjectComponentIdentifier getIdentifierForProject(Path projectPath) {
        BuildIdentifier buildIdentifier = getBuildIdentifier();
        Path identityPath = getIdentityPathForProject(projectPath);
        DefaultProjectDescriptor project = getLoadedSettings().getProjectRegistry().getProject(projectPath.getPath());
        if (project == null) {
            throw new IllegalArgumentException("Project " + projectPath + " not found.");
        }
        String name = project.getName();
        return new DefaultProjectComponentIdentifier(buildIdentifier, identityPath, projectPath, name);
    }
}
