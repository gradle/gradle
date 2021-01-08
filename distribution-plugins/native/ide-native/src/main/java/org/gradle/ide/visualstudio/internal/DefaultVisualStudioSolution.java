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
import org.gradle.api.Transformer;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.ide.visualstudio.TextConfigFile;
import org.gradle.ide.visualstudio.TextProvider;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.plugins.ide.internal.IdeArtifactRegistry;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public class DefaultVisualStudioSolution implements VisualStudioSolutionInternal {
    private final String name;
    private final SolutionFile solutionFile;
    private final DefaultTaskDependency buildDependencies = new DefaultTaskDependency();
    private final IdeArtifactRegistry ideArtifactRegistry;
    private final Provider<RegularFile> location;

    @Inject
    public DefaultVisualStudioSolution(String name, ObjectFactory objectFactory, IdeArtifactRegistry ideArtifactRegistry, ProviderFactory providers, ProjectLayout projectLayout) {
        this.name = name;
        this.solutionFile = objectFactory.newInstance(SolutionFile.class, getName() + ".sln");
        this.location = projectLayout.file(providers.provider(new Callable<File>() {
            @Override
            public File call() {
                return solutionFile.getLocation();
            }
        }));
        this.ideArtifactRegistry = ideArtifactRegistry;
        builtBy(ideArtifactRegistry.getIdeProjectFiles(VisualStudioProjectMetadata.class));
    }

    @Override
    public String getDisplayName() {
        return "Visual Studio solution";
    }

    @Override
    public Provider<RegularFile> getLocation() {
        return location;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public SolutionFile getSolutionFile() {
        return solutionFile;
    }

    @Override
    @Internal
    public List<VisualStudioProjectMetadata> getProjects() {
        return CollectionUtils.collect(ideArtifactRegistry.getIdeProjects(VisualStudioProjectMetadata.class), new Transformer<VisualStudioProjectMetadata, IdeArtifactRegistry.Reference<VisualStudioProjectMetadata>>() {
            @Override
            public VisualStudioProjectMetadata transform(IdeArtifactRegistry.Reference<VisualStudioProjectMetadata> reference) {
                return reference.get();
            }
        });
    }

    @Input
    public List<String> getProjectFilePaths() {
        return CollectionUtils.collect(getProjects(), new Transformer<String, VisualStudioProjectMetadata>() {
            @Override
            public String transform(VisualStudioProjectMetadata metadata) {
                return metadata.getFile().getAbsolutePath();
            }
        });
    }

    @Input
    public Set<String> getConfigurationNames() {
        return getProjects().stream()
                .flatMap(it -> it.getConfigurations().stream().map(VisualStudioProjectConfigurationMetadata::getName))
                .collect(Collectors.toSet());
    }

    @Nested
    public List<Action<? super TextProvider>> getSolutionFileActions() {
        return solutionFile.getTextActions();
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

        @Inject
        public SolutionFile(PathToFileResolver fileResolver, String defaultLocation) {
            this.fileResolver = fileResolver;
            this.location = defaultLocation;
        }

        @Override
        @Internal
        public File getLocation() {
            return fileResolver.resolve(location);
        }

        @Override
        public void setLocation(Object location) {
            this.location = location;
        }

        @Override
        public void withContent(Action<? super TextProvider> action) {
            actions.add(action);
        }

        @Nested
        public List<Action<? super TextProvider>> getTextActions() {
            return actions;
        }
    }
}
