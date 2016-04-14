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

package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import com.google.common.collect.Sets;
import org.apache.ivy.core.module.descriptor.ExcludeRule;
import org.gradle.api.*;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.initialization.ReportedException;
import org.gradle.internal.component.local.model.*;
import org.gradle.internal.component.model.ComponentArtifactMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.util.CollectionUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.Collections;
import java.util.Set;

public class CompositeContextBuilder implements BuildActionRunner {
    private final DefaultCompositeBuildContext context = new DefaultCompositeBuildContext();
    private final boolean propagateFailures;

    public CompositeContextBuilder(boolean propagateFailures) {
        this.propagateFailures = propagateFailures;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        try {
            GradleInternal gradle = buildController.configure();
            ProjectInternal rootProject = gradle.getRootProject();

            String participantName = rootProject.getName();
            for (Project project : rootProject.getAllprojects()) {
                registerProject(participantName, (ProjectInternal) project);
            }
        } catch(ReportedException e) {
            // Ignore exceptions creating composite context
            // TODO:DAZ Handle this better. Test coverage.
            if (propagateFailures) {
                throw e;
            }
        }
    }

    private void registerProject(String buildName, ProjectInternal project) {
        String projectPath = buildName + ":" + project.getPath();
        ProjectComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(projectPath);

        ProjectComponentRegistry projectComponentRegistry = project.getServices().get(ProjectComponentRegistry.class);

        DefaultLocalComponentMetaData projectComponentMetadata = (DefaultLocalComponentMetaData) projectComponentRegistry.getProject(project.getPath());
        LocalComponentMetaData localComponentMetaData = createCompositeCopy(componentIdentifier, projectComponentMetadata);

        context.register(localComponentMetaData.getId().getModule(), projectPath, localComponentMetaData, project.getProjectDir());
    }

    private LocalComponentMetaData createCompositeCopy(ProjectComponentIdentifier componentIdentifier, DefaultLocalComponentMetaData projectComponentMetadata) {
        DefaultLocalComponentMetaData compositeComponentMetadata = new DefaultLocalComponentMetaData(projectComponentMetadata.getId(), componentIdentifier, projectComponentMetadata.getStatus());

        for (String configurationName : projectComponentMetadata.getConfigurationNames()) {
            LocalConfigurationMetaData configuration = projectComponentMetadata.getConfiguration(configurationName);
            // TODO:DAZ Should really be doing this per-artifact, rather than having a configuration dependency
            TaskDependency configurationTaskDependency = createTaskDependency(componentIdentifier.getProjectPath(), configuration);
            compositeComponentMetadata.addConfiguration(configurationName,
                configuration.getDescription(), configuration.getExtendsFrom(), configuration.getHierarchy(),
                configuration.isVisible(), configuration.isTransitive(), configurationTaskDependency);


            // TODO:DAZ Probably shouldn't need to convert back into PublishArtifact.
            Set<ComponentArtifactMetaData> artifacts = configuration.getArtifacts();
            Set<PublishArtifact> detachedArtifacts = CollectionUtils.collect(artifacts, new Transformer<PublishArtifact, ComponentArtifactMetaData>() {
                @Override
                public PublishArtifact transform(ComponentArtifactMetaData componentArtifactMetaData) {
                    return new DetachedPublishArtifact(componentArtifactMetaData.getName(), ((LocalComponentArtifactIdentifier) componentArtifactMetaData).getFile());
                }
            });

            compositeComponentMetadata.addArtifacts(configurationName, detachedArtifacts);
        }
        for (DependencyMetaData dependency : projectComponentMetadata.getDependencies()) {
            compositeComponentMetadata.addDependency(dependency);
        }
        for (ExcludeRule excludeRule : projectComponentMetadata.getExcludeRules()) {
            compositeComponentMetadata.addExcludeRule(excludeRule);
        }

        return compositeComponentMetadata;
    }

    public CompositeBuildContext build() {
        return context;
    }

    private TaskDependency createTaskDependency(final String projectPath, LocalConfigurationMetaData configuration) {
        final Set<String> targetTaskNames = determineTaskNames(configuration);
        final String taskName = "composite_" + projectPath + "_" + configuration.getName();
        return new TaskDependency() {
            @Override
            public Set<? extends Task> getDependencies(Task task) {
                TaskContainer tasks = task.getProject().getRootProject().getTasks();
                Task depTask = tasks.findByName(taskName);
                if (depTask == null) {
                    depTask = tasks.create(taskName, CompositeProjectBuild.class, new Action<CompositeProjectBuild>() {
                        @Override
                        public void execute(CompositeProjectBuild buildTask) {
                            buildTask.conf(projectPath, targetTaskNames);
                        }
                    });
                }
                return Collections.singleton(depTask);
            }
        };
    }

    private Set<String> determineTaskNames(LocalConfigurationMetaData configuration) {
        Set<String> taskNames = Sets.newLinkedHashSet();
        for (ComponentArtifactMetaData artifactMetaData : configuration.getArtifacts()) {
            if (artifactMetaData instanceof Buildable) {
                Buildable publishArtifact = (Buildable) artifactMetaData;
                Set<? extends Task> dependencies = publishArtifact.getBuildDependencies().getDependencies(null);
                for (Task dependency : dependencies) {
                    taskNames.add(dependency.getName());
                }
            }
        }
        return taskNames;
    }

    public static class CompositeProjectBuild extends DefaultTask {
        private final CompositeProjectArtifactBuilder artifactBuilder;
        private String projectPath;
        private Set<String> taskNames;

        @Inject
        public CompositeProjectBuild(CompositeProjectArtifactBuilder artifactBuilder) {
            this.artifactBuilder = artifactBuilder;
        }

        public void conf(String path, Set<String> taskNames) {
            projectPath = path;
            this.taskNames = taskNames;
        }

        @TaskAction
        public void build() {
            artifactBuilder.build(projectPath, taskNames);
        }
    }


    private static class DetachedPublishArtifact extends DefaultPublishArtifact {
        public DetachedPublishArtifact(IvyArtifactName ivyArtifactName, File artifactFile) {
            super(ivyArtifactName.getName(), ivyArtifactName.getExtension(), ivyArtifactName.getType(), ivyArtifactName.getClassifier(), null, artifactFile);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DetachedPublishArtifact)) {
                return false;
            }

            DetachedPublishArtifact that = (DetachedPublishArtifact) o;
            return getFile().equals(that.getFile());

        }

        @Override
        public int hashCode() {
            return getFile().hashCode();
        }
    }

}
