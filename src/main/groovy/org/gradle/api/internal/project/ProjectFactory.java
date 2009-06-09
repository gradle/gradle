/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.internal.project;

import org.gradle.api.initialization.ProjectDescriptor;
import org.gradle.api.internal.BuildInternal;
import org.gradle.groovy.scripts.FileScriptSource;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ProjectFactory implements IProjectFactory {
    private ProjectServiceRegistryFactory serviceRegistryFactory;
    private ScriptSource embeddedScript;

    public ProjectFactory() {
    }

    public ProjectFactory(ProjectServiceRegistryFactory serviceRegistryFactory, ScriptSource embeddedScript) {
        this.serviceRegistryFactory = serviceRegistryFactory;
        this.embeddedScript = embeddedScript;
    }

    public DefaultProject createProject(ProjectDescriptor projectDescriptor, ProjectInternal parent, BuildInternal build) {
        File buildFile = projectDescriptor.getBuildFile();
        ScriptSource source;
        if (embeddedScript != null) {
            source = embeddedScript;
        } else if (!buildFile.exists()) {
            source = new StringScriptSource("empty build file", "");
        } else {
            source = new FileScriptSource("build file", buildFile);
        }

        DefaultProject project = new DefaultProject(
                projectDescriptor.getName(),
                parent,
                projectDescriptor.getProjectDir(),
                projectDescriptor.getBuildFile(),
                source,
                build.getBuildScriptClassLoader(),
                build.getPluginRegistry(),
                build.getProjectRegistry(),
                build,
                serviceRegistryFactory);

        if (parent != null) {
            parent.addChildProject(project);
        }
        build.getProjectRegistry().addProject(project);

        return project;
    }
}