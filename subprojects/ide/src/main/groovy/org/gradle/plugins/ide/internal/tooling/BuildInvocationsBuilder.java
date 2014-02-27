/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import com.google.common.collect.*;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.model.TaskSelector;
import org.gradle.tooling.model.internal.ProjectSensitiveToolingModelBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class BuildInvocationsBuilder extends ProjectSensitiveToolingModelBuilder {
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        Multimap<String, String> aggregatedTasks = findTasks(project);
        for (String selectorName : aggregatedTasks.keySet()) {
            selectors.add(new DefaultGradleTaskSelector().
                    setName(selectorName).
                    setTaskNames(Sets.newHashSet(aggregatedTasks.get(selectorName))).
                    setDescription(project.getParent() != null ?
                            String.format("%s:%s task selector", project.getPath(), selectorName) :
                            String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s built in %s and subprojects.", selectorName, project.getName())));
        }
        return new DefaultBuildInvocations().setSelectors(selectors);
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project, boolean implicitProject) {
        if (implicitProject) {
            return new DefaultBuildInvocations().setSelectors(buildRecursively(modelName, project.getRootProject()));
        } else {
            return buildAll(modelName, project);
        }
    }

    private List<DefaultGradleTaskSelector> buildRecursively(String modelName, Project project) {
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        selectors.addAll(buildAll(modelName, project).getTaskSelectors());
        for (Project childProject : project.getSubprojects()) {
            selectors.addAll(buildRecursively(modelName, childProject));
        }
        return selectors;
    }

    public Multimap<String, String> findTasks(Project project) {
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();
        for (Project child : project.getSubprojects()) {
            Multimap<String, String> childTasks = findTasks(child);
            aggregatedTasks.putAll(childTasks);
        }
        for (Task task : project.getTasks()) {
            aggregatedTasks.put(task.getName(), task.getPath());
        }
        return aggregatedTasks;
    }

}
