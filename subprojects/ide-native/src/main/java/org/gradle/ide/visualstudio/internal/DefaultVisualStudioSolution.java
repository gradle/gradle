/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.ide.visualstudio.internal;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.visualstudio.TextConfigFile;
import org.gradle.ide.visualstudio.TextProvider;
import org.gradle.ide.visualstudio.VisualStudioProject;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.Instantiator;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultVisualStudioSolution implements VisualStudioSolutionInternal {
    private final String name;
    private final DefaultVisualStudioProject rootProject;
    private final SolutionFile solutionFile;
    private final VisualStudioProjectResolver vsProjectResolver;
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();

    public DefaultVisualStudioSolution(String name, DefaultVisualStudioProject rootProject, PathToFileResolver fileResolver, VisualStudioProjectResolver vsProjectResolver, Instantiator instantiator) {
        this.name = name;
        this.rootProject = rootProject;
        this.vsProjectResolver = vsProjectResolver;
        this.solutionFile = instantiator.newInstance(SolutionFile.class, fileResolver, getName() + ".sln");
    }

    @Override
    public String getName() {
        return name;
    }

    public SolutionFile getSolutionFile() {
        return solutionFile;
    }

    @Override
    public String getComponentName() {
        return rootProject.getComponentName();
    }

    public Set<VisualStudioProject> getProjects() {
        Set<VisualStudioProject> projects = new LinkedHashSet<VisualStudioProject>();

        for (VisualStudioProjectConfiguration solutionConfig : getSolutionConfigurations()) {
            for (VisualStudioProjectConfiguration projectConfig : getProjectConfigurations(solutionConfig)) {
                projects.add(projectConfig.getProject());
            }
        }

        return projects;
    }

    public List<VisualStudioProjectConfiguration> getSolutionConfigurations() {
        return rootProject.getConfigurations();
    }

    public List<VisualStudioProjectConfiguration> getProjectConfigurations(VisualStudioProjectConfiguration solutionConfiguration) {
        Set<VisualStudioProjectConfiguration> configurations = new LinkedHashSet<VisualStudioProjectConfiguration>();
        configurations.add(solutionConfiguration);
        addDependentConfigurations(configurations, solutionConfiguration);
        return new ArrayList<VisualStudioProjectConfiguration>(configurations);
    }

    private void addDependentConfigurations(Set configurations, VisualStudioProjectConfiguration configuration) {
        for (VisualStudioTargetBinary library : configuration.getTargetBinary().getDependencies()) {
            VisualStudioProjectConfiguration libraryConfiguration = vsProjectResolver.lookupProjectConfiguration(library);
            if (configurations.add(libraryConfiguration)) {
                addDependentConfigurations(configurations, libraryConfiguration);
            }
        }

    }

    public final DefaultVisualStudioProject getRootProject() {
        return rootProject;
    }

    @Override
    public void builtBy(Object... tasks) {
        buildDependencies.add(tasks);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return buildDependencies;
    }

    public static class SolutionFile implements TextConfigFile {
        private final List<Action<? super TextProvider>> actions = new ArrayList<Action<? super TextProvider>>();
        private final PathToFileResolver fileResolver;
        private Object location;

        public SolutionFile(PathToFileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver;
            this.location = defaultLocation;
        }

        public File getLocation() {
            return fileResolver.resolve(location);
        }

        public void setLocation(Object location) {
            this.location = location;
        }

        public void withContent(Action<? super TextProvider> action) {
            actions.add(action);
        }

        public List<Action<? super TextProvider>> getTextActions() {
            return actions;
        }
    }
}
