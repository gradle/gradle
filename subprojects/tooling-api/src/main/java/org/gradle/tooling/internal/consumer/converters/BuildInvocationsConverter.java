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

package org.gradle.tooling.internal.consumer.converters;

import com.google.common.base.Function;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.gradle.BasicGradleTaskSelector;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.Task;

import java.util.Iterator;
import java.util.List;

public class BuildInvocationsConverter {
    public DefaultBuildInvocations<Task> convert(GradleProject project, ProtocolToModelAdapter adapter) {
        List<GradleTask> tasks = Lists.newArrayList();
        GradleProject rootProject = project;
        while (rootProject.getParent() != null) {
            rootProject = rootProject.getParent();
        }
        List<BasicGradleTaskSelector> selectors = buildRecursively(rootProject, tasks);
        return new DefaultBuildInvocations<Task>()
                .setSelectors(selectors)
                .setTasks(convertTasks(rootProject.getTasks().iterator(), adapter));
    }

    private List<BasicGradleTaskSelector> buildRecursively(GradleProject project, List<GradleTask> tasks) {
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();
        for (GradleProject childProject : project.getChildren()) {
            List<BasicGradleTaskSelector> childSelectors = buildRecursively(childProject, tasks);
            for (BasicGradleTaskSelector childSelector : childSelectors) {
                aggregatedTasks.putAll(childSelector.getName(), childSelector.getTaskNames());
            }
        }
        for (GradleTask task : project.getTasks()) {
            aggregatedTasks.put(task.getName(), task.getPath());
            tasks.add(task);
        }
        List<BasicGradleTaskSelector> selectors = Lists.newArrayList();
        for (String selectorName : aggregatedTasks.keySet()) {
            selectors.add(new BasicGradleTaskSelector().
                    setName(selectorName).
                    setTaskNames(Sets.newHashSet(aggregatedTasks.get(selectorName))).
                    setDescription(project.getParent() != null
                            ? String.format("%s:%s task selector", project.getPath(), selectorName)
                            : String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.getName())));
        }
        return selectors;
    }

    private List<Task> convertTasks(Iterator<? extends GradleTask> tasks, final ProtocolToModelAdapter adapter) {
        return Lists.newArrayList(Iterators.transform(
                tasks,
                new Function<GradleTask, Task>() {
                    public Task apply(GradleTask task) {
                        return adapter.adapt(Task.class, new DefaultGradleTask()
                                .setName(task.getName())
                                .setPath(task.getPath())
                                .setDescription(task.getDescription()));
                    }
                }
        ));
    }
}
