/*
 * Copyright 2021 the original author or authors.
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

package org.gradleinternal.buildinit.plugins.internal.maven;

public class ProjectDependency extends Dependency {
    private final String projectPath;

    public ProjectDependency(String configuration, String projectPath) {
        super(configuration);
        this.projectPath = projectPath;
    }

    public String getProjectPath() {
        return projectPath;
    }
}
