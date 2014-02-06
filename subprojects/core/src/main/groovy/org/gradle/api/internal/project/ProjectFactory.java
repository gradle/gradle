/*
 * Copyright 2010 the original author or authors.
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
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.StringScriptSource;
import org.gradle.groovy.scripts.UriScriptSource;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;

public class ProjectFactory implements IProjectFactory {
    private final Instantiator instantiator;
    private final ProjectRegistry<ProjectInternal> projectRegistry;

    public ProjectFactory(Instantiator instantiator, ProjectRegistry<ProjectInternal> projectRegistry) {
        this.instantiator = instantiator;
        this.projectRegistry = projectRegistry;
    }

    public DefaultProject createProject(ProjectDescriptor projectDescriptor, ProjectInternal parent, GradleInternal gradle, ClassLoaderScope classLoaderScope) {
        File buildFile = projectDescriptor.getBuildFile();
        ScriptSource source;
        if (!buildFile.exists()) {
            source = new StringScriptSource("empty build file", "");
        } else {
            source = new UriScriptSource("build file", buildFile);
        }

        DefaultProject project = instantiator.newInstance(DefaultProject.class,
                projectDescriptor.getName(),
                parent,
                projectDescriptor.getProjectDir(),
                source,
                gradle,
                gradle.getServiceRegistryFactory(),
                classLoaderScope
                );

        if (parent != null) {
            parent.addChildProject(project);
        }
        projectRegistry.addProject(project);

        return project;
    }
}