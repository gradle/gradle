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
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.TextResource;
import org.gradle.util.internal.NameValidator;

import javax.annotation.Nullable;
import java.io.File;

public class ProjectFactory implements IProjectFactory {
    private final Instantiator instantiator;
    private final TextFileResourceLoader textFileResourceLoader;

    public ProjectFactory(Instantiator instantiator, TextFileResourceLoader textFileResourceLoader) {
        this.instantiator = instantiator;
        this.textFileResourceLoader = textFileResourceLoader;
    }

    @Override
    public ProjectInternal createProject(GradleInternal gradle, ProjectDescriptor projectDescriptor, ProjectState owner, @Nullable ProjectInternal parent, ClassLoaderScope selfClassLoaderScope, ClassLoaderScope baseClassLoaderScope) {
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
            owner,
            gradle.getServiceRegistryFactory(),
            selfClassLoaderScope,
            baseClassLoaderScope
        );
        project.beforeEvaluate(p -> {
            NameValidator.validate(project.getName(), "project name", DefaultProjectDescriptor.INVALID_NAME_IN_INCLUDE_HINT);
            gradle.getServices().get(DependenciesAccessors.class).createExtensions(project);
            gradle.getServices().get(DependencyResolutionManagementInternal.class).configureProject(project);
        });

        gradle.getProjectRegistry().addProject(project);
        return project;
    }
}
