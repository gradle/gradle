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

import org.gradle.api.internal.initialization.ClassLoaderScope;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.groovy.scripts.TextResourceScriptSource;
import org.gradle.initialization.DefaultProjectDescriptor;
import org.gradle.initialization.DependenciesAccessors;
import org.gradle.internal.management.DependencyResolutionManagementInternal;
import org.gradle.internal.project.ImmutableProjectDescriptor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.TextFileResourceLoader;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.scripts.ProjectScopedScriptResolution;
import org.gradle.internal.service.scopes.ServiceRegistryFactory;
import org.gradle.util.internal.NameValidator;

import java.io.File;

public class ProjectFactory implements IProjectFactory {

    private final Instantiator instantiator;
    private final TextFileResourceLoader textFileResourceLoader;
    private final ProjectScopedScriptResolution scriptResolution;
    private final DependencyResolutionManagementInternal dependencyResolutionManagement;
    private final DependenciesAccessors dependenciesAccessors;
    private final ProjectRegistry projectRegistry;

    public ProjectFactory(
        Instantiator instantiator,
        TextFileResourceLoader textFileResourceLoader,
        ProjectScopedScriptResolution scriptResolution,
        DependencyResolutionManagementInternal dependencyResolutionManagement,
        DependenciesAccessors dependenciesAccessors,
        ProjectRegistry projectRegistry
    ) {
        this.instantiator = instantiator;
        this.textFileResourceLoader = textFileResourceLoader;
        this.scriptResolution = scriptResolution;
        this.dependencyResolutionManagement = dependencyResolutionManagement;
        this.dependenciesAccessors = dependenciesAccessors;
        this.projectRegistry = projectRegistry;
    }

    @Override
    public ProjectInternal createProject(
        ProjectState owner,
        ClassLoaderScope selfClassLoaderScope,
        ClassLoaderScope baseClassLoaderScope,
        ServiceRegistryFactory serviceRegistryFactory
    ) {
        ImmutableProjectDescriptor projectDescriptor = owner.getDescriptor();
        // Need to wrap resolution of the build file to associate the build file with the correct project
        File buildFile = scriptResolution.resolveScriptsForProject(owner.getIdentity(), projectDescriptor::getBuildFile);
        TextResource resource = textFileResourceLoader.loadFile("build file", buildFile);
        ScriptSource source = new TextResourceScriptSource(resource);
        DefaultProject project = instantiator.newInstance(DefaultProject.class,
            owner,
            selfClassLoaderScope,
            baseClassLoaderScope,
            source,
            serviceRegistryFactory
        );
        dependencyResolutionManagement.configureProject(project);
        project.beforeEvaluate(p -> {
            NameValidator.validate(project.getName(), "project name", DefaultProjectDescriptor.INVALID_NAME_IN_INCLUDE_HINT);
            dependenciesAccessors.createExtensions(project);
        });
        projectRegistry.addProject(project);
        return project;
    }
}
