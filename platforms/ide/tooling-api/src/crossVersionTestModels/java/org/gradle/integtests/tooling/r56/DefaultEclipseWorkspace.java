/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.integtests.tooling.r56;

import org.gradle.tooling.model.eclipse.EclipseWorkspace;
import org.gradle.tooling.model.eclipse.EclipseWorkspaceProject;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class DefaultEclipseWorkspace implements EclipseWorkspace, Serializable {

    private final File location;
    private final List<EclipseWorkspaceProject> workspaceProjects;

    public DefaultEclipseWorkspace(File location, List<EclipseWorkspaceProject> workspaceProjects) {
        this.location = location;
        this.workspaceProjects = workspaceProjects;
    }

    @Override
    public File getLocation() {
        return location;
    }

    @Override
    public List<EclipseWorkspaceProject> getProjects() {
        return workspaceProjects;
    }
}
