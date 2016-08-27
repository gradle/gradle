/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Preconditions;
import groovy.util.Node;
import org.gradle.api.Incubating;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

/**
 * A classpath entry representing a project dependency.
 */
public class ProjectDependency extends AbstractClasspathEntry {

    private ProjectComponentIdentifier projectId;

    public ProjectDependency(Node node) {
        super(node);
        assertPathIsValid();
    }

    public ProjectDependency(String path, String gradlePath) {
        super(path);
        assertPathIsValid();
        setProjectIdFromGradlePath(gradlePath);
    }

    public ProjectDependency(ProjectComponentIdentifier projectId, String path) {
        super(path);
        assertPathIsValid();
        this.projectId = projectId;
    }

    private void setProjectIdFromGradlePath(String gradlePath) {
        this.projectId = gradlePath == null ? null : newProjectId(gradlePath);
    }

    public String getGradlePath() {
        return projectId == null ? null : projectId.getProjectPath();
    }

    @Deprecated
    public void setGradlePath(String gradlePath) {
        setProjectIdFromGradlePath(gradlePath);
    }

    @Incubating
    public ProjectComponentIdentifier getGradleProjectId() {
        return projectId;
    }

    private void assertPathIsValid() {
        Preconditions.checkArgument(path.startsWith("/"));
    }

    @Override
    public String getKind() {
        return "src";
    }

    @Override
    public String toString() {
        return "ProjectDependency" + super.toString();
    }
}
