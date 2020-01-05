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
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.internal.build.BuildState;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.TextResource;
import org.gradle.util.NameValidator;
import org.gradle.util.Path;

import javax.annotation.Nullable;
import java.io.File;

public class ProjectFactory implements IProjectFactory {
    private final Instantiator instantiator;
    private final TextFileResourceLoader textFileResourceLoader;
    private final ProjectRegistry<ProjectInternal> projectRegistry;
    private final BuildState owner;
    private final ProjectStateRegistry projectStateRegistry;

    public ProjectFactory(Instantiator instantiator, TextFileResourceLoader textFileResourceLoader, ProjectRegistry<ProjectInternal> projectRegistry, BuildState owner, ProjectStateRegistry projectStateRegistry) {
        this.instantiator = instantiator;
        this.textFileResourceLoader = textFileResourceLoader;
        this.projectRegistry = projectRegistry;
        this.owner = owner;
        this.projectStateRegistry = projectStateRegistry;
    }

    @Override
    public ProjectInternal createProject(GradleInternal gradle, ProjectDescriptor projectDescriptor, @Nullable ProjectInternal parent, ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
        Path projectPath = ((DefaultProjectDescriptor) projectDescriptor).path();
        ProjectState projectContainer = projectStateRegistry.stateFor(owner.getBuildIdentifier(), projectPath);

        File buildFile = projectDescriptor.getBuildFile();
        TextResource resource = textFileResourceLoader.loadFile("build file", buildFile);
        ScriptSource source = new TextResourceScriptSource(resource);
        DefaultProject project = instantiator.newInstance(DefaultProject.class,
            projectDescriptor.getName(),
            parent,
            projectDescriptor.getProjectDir(),
            buildFile,
            source,
            gradle,
            projectContainer,
            gradle.getServiceRegistryFactory(),
            selfClassLoaderScope,
            baseClassLoaderScope
        );
        project.beforeEvaluate(p -> NameValidator.validate(project.getName(), "project name", DefaultProjectDescriptor.INVALID_NAME_IN_INCLUDE_HINT));

        if (parent != null) {
            parent.addChildProject(project);
        }
        projectRegistry.addProject(project);
        projectContainer.attachMutableModel(project);
        return project;
    }
}
