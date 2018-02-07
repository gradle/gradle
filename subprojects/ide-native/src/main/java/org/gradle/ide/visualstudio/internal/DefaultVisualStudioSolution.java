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
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DefaultVisualStudioSolution implements VisualStudioSolutionInternal {
    private final String name;
    private final SolutionFile solutionFile;
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final IdeArtifactRegistry ideArtifactRegistry;

    public DefaultVisualStudioSolution(String name, PathToFileResolver fileResolver, Instantiator instantiator, IdeArtifactRegistry ideArtifactRegistry) {
        this.name = name;
        this.solutionFile = instantiator.newInstance(SolutionFile.class, fileResolver, getName() + ".sln");
        this.ideArtifactRegistry = ideArtifactRegistry;
        builtBy(ideArtifactRegistry.getIdeArtifacts(VisualStudioProjectInternal.ARTIFACT_TYPE));
        builtBy(ideArtifactRegistry.getIdeArtifacts(VisualStudioProjectConfiguration.ARTIFACT_TYPE));
    }

    @Override
    public String getName() {
        return name;
    }

    public SolutionFile getSolutionFile() {
        return solutionFile;
    }

    @Override
    public List<LocalComponentArtifactMetadata> getProjectArtifacts() {
        return ideArtifactRegistry.getIdeArtifactMetadata(VisualStudioProjectInternal.ARTIFACT_TYPE);
    }

    @Override
    public List<LocalComponentArtifactMetadata> getProjectConfigurationArtifacts() {
        return ideArtifactRegistry.getIdeArtifactMetadata(VisualStudioProjectConfiguration.ARTIFACT_TYPE);
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
