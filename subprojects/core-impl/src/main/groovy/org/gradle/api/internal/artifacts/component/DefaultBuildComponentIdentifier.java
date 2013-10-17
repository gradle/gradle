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
package org.gradle.api.internal.artifacts.component;

import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildComponentIdentifier;

public class DefaultBuildComponentIdentifier implements BuildComponentIdentifier {
    private final Project project;
    private final String displayName;

    public DefaultBuildComponentIdentifier(Project project) {
        assert project != null : "project cannot be null";
        this.project = project;
        displayName = String.format("project %s", project.getPath());
    }

    public String getDisplayName() {
        return displayName;
    }

    public Project getProject() {
        return project;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultBuildComponentIdentifier that = (DefaultBuildComponentIdentifier) o;

        if (!project.equals(that.project)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return project.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }
}
