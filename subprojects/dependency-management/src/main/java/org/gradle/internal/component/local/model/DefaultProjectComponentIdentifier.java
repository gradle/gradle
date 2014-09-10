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

import org.gradle.api.artifacts.component.ProjectComponentIdentifier;

public class DefaultProjectComponentIdentifier implements ProjectComponentIdentifier {
    private final String projectPath;
    private final String displayName;

    public DefaultProjectComponentIdentifier(String projectPath) {
        assert projectPath != null : "project path cannot be null";
        this.projectPath = projectPath;
        displayName = String.format("project %s", projectPath);
    }

    public String getDisplayName() {
        return displayName;
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

        if (!projectPath.equals(that.projectPath)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return projectPath.hashCode();
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static ProjectComponentIdentifier newId(String projectPath) {
        return new DefaultProjectComponentIdentifier(projectPath);
    }
}
