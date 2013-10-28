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

import org.gradle.api.artifacts.component.BuildComponentIdentifier;
import org.gradle.api.artifacts.component.BuildComponentSelector;
import org.gradle.api.artifacts.component.ComponentIdentifier;

public class DefaultBuildComponentSelector implements BuildComponentSelector {
    private final String projectPath;
    private final String displayName;

    public DefaultBuildComponentSelector(String projectPath) {
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

    public boolean matchesStrictly(ComponentIdentifier identifier) {
        assert identifier != null : "identifier cannot be null";

        if(identifier instanceof BuildComponentIdentifier) {
            BuildComponentIdentifier buildComponentIdentifier = (BuildComponentIdentifier)identifier;
            return projectPath.equals(buildComponentIdentifier.getProjectPath());
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultBuildComponentSelector that = (DefaultBuildComponentSelector) o;

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
}
