/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.resolver.model;

import org.gradle.api.Project;

public class IdeProjectDependency extends IdeDependency {
    private final Project project;
    private final String projectPath;

    public IdeProjectDependency(String declaredConfiguration, Project project) {
        super(declaredConfiguration);
        this.project = project;
        this.projectPath = project.getPath();
    }

    public IdeProjectDependency(String declaredConfiguration, String projectPath) {
        super(declaredConfiguration);
        this.project = null;
        this.projectPath = projectPath;
    }

    public Project getProject() {
        return project;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getModuleName() {
        // This is just a hack to allow 'idea' task to function reasonably in a composite
        // This will be addressed when we add support for IDE project file generation for a composite build
        if (projectPath.endsWith("::")) {
            return projectPath.substring(0, projectPath.length() - 2);
        }
        int index = projectPath.lastIndexOf(':');
        return projectPath.substring(index + 1);
    }
}
