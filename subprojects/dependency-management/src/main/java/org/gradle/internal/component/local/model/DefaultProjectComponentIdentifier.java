/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model;

import com.google.common.base.Objects;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.component.DefaultBuildIdentifier;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.initialization.BuildIdentity;

public class DefaultProjectComponentIdentifier implements ProjectComponentIdentifier {
    private static final Interner<DefaultProjectComponentIdentifier> CURRENT_INSTANCES_INTERNER = Interners.newWeakInterner();
    private static final Interner<DefaultProjectComponentIdentifier> OTHER_INSTANCES_INTERNER = Interners.newStrongInterner();
    private final BuildIdentifier buildIdentifier;
    private final String projectPath;
    private String displayName;

    private DefaultProjectComponentIdentifier(BuildIdentifier buildIdentifier, String projectPath) {
        assert buildIdentifier != null : "build cannot be null";
        assert projectPath != null : "project path cannot be null";
        this.buildIdentifier = buildIdentifier;
        this.projectPath = projectPath;
    }

    public static DefaultProjectComponentIdentifier of(BuildIdentifier buildIdentifier, String projectPath) {
        DefaultProjectComponentIdentifier instance = new DefaultProjectComponentIdentifier(buildIdentifier, projectPath);
        // BuildIdentifier contains state which isn't part of equals/hashCode
        // DefaultBuildableCompositeBuildContext.ensureRegistered depends on this state
        if (buildIdentifier.isCurrentBuild()) {
            return CURRENT_INSTANCES_INTERNER.intern(instance);
        } else {
            return OTHER_INSTANCES_INTERNER.intern(instance);
        }
    }

    private String createDisplayName(BuildIdentifier buildIdentifier, String projectPath) {
        return "project " + fullPath(buildIdentifier, projectPath);
    }

    public String getDisplayName() {
        if (displayName == null) {
            displayName = createDisplayName(buildIdentifier, projectPath);
        }
        return displayName;
    }

    @Override
    public BuildIdentifier getBuild() {
        return buildIdentifier;
    }

    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectComponentIdentifier that = (DefaultProjectComponentIdentifier) o;
        return Objects.equal(projectPath, that.projectPath)
            && Objects.equal(buildIdentifier, that.buildIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectPath, buildIdentifier);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    private static String fullPath(BuildIdentifier build, String projectPath) {
        if (build.isCurrentBuild()) {
            return projectPath;
        }
        return ":" + build.getName() + projectPath;
    }

    public static ProjectComponentIdentifier newProjectId(IncludedBuild build, String projectPath) {
        BuildIdentifier buildIdentifier = DefaultBuildIdentifier.of(build.getName());
        return of(buildIdentifier, projectPath);
    }

    public static ProjectComponentIdentifier newProjectId(Project project) {
        BuildIdentifier buildId = ((ProjectInternal) project).getServices().get(BuildIdentity.class).getCurrentBuild();
        return of(buildId, project.getPath());
    }

}
