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
package org.gradle.plugins.ide.internal;

import com.google.common.collect.Lists;
import org.apache.commons.lang.StringUtils;
import org.gradle.BuildAdapter;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry;
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.ProjectLocalComponentProvider;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtraPropertiesExtension;
import org.gradle.api.tasks.Delete;
import org.gradle.initialization.ProjectPathRegistry;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.CollectionUtils;
import org.gradle.util.Path;

import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier.newProjectId;

public abstract class IdePlugin implements Plugin<Project> {

    private Task lifecycleTask;
    private Task cleanTask;
    protected Project project;

    @Override
    public void apply(Project target) {
        project = target;
        String lifecycleTaskName = getLifecycleTaskName();
        lifecycleTask = target.task(lifecycleTaskName);
        lifecycleTask.setGroup("IDE");
        cleanTask = target.task(cleanName(lifecycleTaskName));
        cleanTask.setGroup("IDE");
        onApply(target);
    }

    public Task getLifecycleTask() {
        return lifecycleTask;
    }

    public Task getCleanTask() {
        return cleanTask;
    }

    public Task getCleanTask(Task worker) {
        return project.getTasks().getByName(cleanName(worker.getName()));
    }

    protected String cleanName(String taskName) {
        return String.format("clean%s", StringUtils.capitalize(taskName));
    }

    public void addWorker(Task worker) {
        addWorker(worker, true);
    }

    public void addWorker(Task worker, boolean includeInClean) {
        lifecycleTask.dependsOn(worker);
        Delete cleanWorker = project.getTasks().create(cleanName(worker.getName()), Delete.class);
        cleanWorker.delete(worker.getOutputs().getFiles());
        if (includeInClean) {
            cleanTask.dependsOn(cleanWorker);
        }
    }

    protected void onApply(Project target) {
    }

    protected abstract String getLifecycleTaskName();

    /**
     * Executes the provided Action after all projects have been evaluated.
     * Action will only be added once per provided key. Any subsequent calls for the same key will be ignored.
     * This permits the plugin to be applied in multiple subprojects, with the postprocess action executed once only.
     */
    protected void postProcess(String key, final Action<? super Gradle> action) {
        Project rootProject = project.getRootProject();
        ExtraPropertiesExtension rootExtraProperties = rootProject.getExtensions().getByType(ExtraPropertiesExtension.class);
        String extraPropertyName = "org.gradle." + key + ".postprocess.applied";
        if (!rootExtraProperties.has(extraPropertyName)) {
            project.getGradle().addBuildListener(new BuildAdapter() {
                @Override
                public void projectsEvaluated(Gradle gradle) {
                    action.execute(gradle);
                }
            });
            rootExtraProperties.set(extraPropertyName, true);
        }
    }

    protected void registerIdeArtifact(PublishArtifact ideArtifact) {
        ProjectLocalComponentProvider projectComponentProvider = ((ProjectInternal) project).getServices().get(ProjectLocalComponentProvider.class);
        ProjectComponentIdentifier projectId = newProjectId(project);
        projectComponentProvider.registerAdditionalArtifact(projectId, new PublishArtifactLocalArtifactMetadata(projectId, ideArtifact));
    }

    public List<LocalComponentArtifactMetadata> getIdeArtifactMetadata(String type) {
        ServiceRegistry serviceRegistry = ((ProjectInternal)project).getServices();
        List<LocalComponentArtifactMetadata> result = Lists.newArrayList();
        ProjectPathRegistry projectPathRegistry = serviceRegistry.get(ProjectPathRegistry.class);
        LocalComponentRegistry localComponentRegistry = serviceRegistry.get(LocalComponentRegistry.class);

        for (Path projectPath : projectPathRegistry.getAllExplicitProjectPaths()) {
            ProjectComponentIdentifier projectId = projectPathRegistry.getProjectComponentIdentifier(projectPath);
            LocalComponentArtifactMetadata artifactMetadata = localComponentRegistry.findAdditionalArtifact(projectId, type);
            if (artifactMetadata != null) {
                result.add(artifactMetadata);
            }
        }

        return result;
    }

    public FileCollection getIdeArtifacts(final String type) {
        return project.files(new Callable<List<FileCollection>>() {
            @Override
            public List<FileCollection> call() throws Exception {
                return CollectionUtils.collect(
                    getIdeArtifactMetadata(type),
                    new Transformer<FileCollection, LocalComponentArtifactMetadata>() {
                        @Override
                        public FileCollection transform(LocalComponentArtifactMetadata metadata) {
                            ConfigurableFileCollection result = project.files(metadata.getFile());
                            result.builtBy(metadata.getBuildDependencies());
                            return result;
                        }
                    });
            }
        });
    }

    public boolean isRoot() {
        return project.getParent() == null;
    }
}
