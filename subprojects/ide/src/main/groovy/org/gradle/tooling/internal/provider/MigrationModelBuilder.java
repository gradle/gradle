/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.tooling.internal.provider;

import com.google.common.collect.Sets;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.tasks.testing.Test;
import org.gradle.tooling.internal.migration.DefaultArchive;
import org.gradle.tooling.internal.migration.DefaultProjectOutput;
import org.gradle.tooling.internal.migration.DefaultTestResult;
import org.gradle.tooling.internal.protocol.InternalProjectOutput;
import org.gradle.tooling.internal.protocol.ProjectVersion3;
import org.gradle.tooling.model.internal.migration.ProjectOutput;
import org.gradle.tooling.model.internal.migration.TaskOutput;

import java.util.Set;

public class MigrationModelBuilder implements BuildsModel {
    public boolean canBuild(Class<?> type) {
        return type == InternalProjectOutput.class;
    }

    public ProjectVersion3 buildAll(GradleInternal gradle) {
        return buildProjectOutput(gradle.getRootProject(), null);

    }

    private DefaultProjectOutput buildProjectOutput(Project project, ProjectOutput parent) {
        Set<TaskOutput> taskOutputs = Sets.newHashSet();
        addArchives(project, taskOutputs);
        addTestResults(project, taskOutputs);

        DefaultProjectOutput projectOutput = new DefaultProjectOutput(project.getName(),
                project.getPath(), project.getDescription(), project.getProjectDir(), taskOutputs, parent);
        for (Project child : project.getChildProjects().values()) {
            projectOutput.addChild(buildProjectOutput(child, projectOutput));
        }

        return projectOutput;
    }

    private void addArchives(Project project, Set<TaskOutput> taskOutputs) {
        Configuration configuration = project.getConfigurations().findByName("archives");
        if (configuration != null) {
            for (PublishArtifact artifact : configuration.getArtifacts()) {
                taskOutputs.add(new DefaultArchive(artifact.getFile()));
            }
        }
    }

    private void addTestResults(Project project, Set<TaskOutput> taskOutputs) {
        Set<Test> testTasks = project.getTasks().withType(Test.class);
        for (Test task : testTasks) {
            taskOutputs.add(new DefaultTestResult(task.getTestResultsDir()));
        }
    }
}
