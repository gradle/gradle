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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.gradle.tooling.internal.gradle.BasicGradleTaskSelector;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTask;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.List;
import java.util.SortedSet;

public class BuildInvocationsConverter {
    public DefaultBuildInvocations<DefaultGradleTask> convert(GradleProject project) {
        GradleProject rootProject = project;
        while (rootProject.getParent() != null) {
            rootProject = rootProject.getParent();
        }
        List<BasicGradleTaskSelector> selectors = buildRecursively(rootProject);
        return new DefaultBuildInvocations<DefaultGradleTask>()
                .setSelectors(selectors)
                .setTasks(convertTasks(rootProject.getTasks()));
    }

    private List<BasicGradleTaskSelector> buildRecursively(GradleProject project) {
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();

        collectTasks(project, aggregatedTasks);

        List<BasicGradleTaskSelector> selectors = Lists.newArrayList();
        for (String selectorName : aggregatedTasks.keySet()) {
            SortedSet<String> selectorTasks = Sets.newTreeSet(new TaskNameComparator());
            selectorTasks.addAll(aggregatedTasks.get(selectorName));
            selectors.add(new BasicGradleTaskSelector().
                    setName(selectorName).
                    setTaskNames(selectorTasks).
                    setDescription(project.getParent() != null
                            ? String.format("%s:%s task selector", project.getPath(), selectorName)
                            : String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.getName())));
        }
        return selectors;
    }

    private void collectTasks(GradleProject project, Multimap<String, String> aggregatedTasks) {
        for (GradleProject childProject : project.getChildren()) {
            collectTasks(childProject, aggregatedTasks);
        }
        for (GradleTask task : project.getTasks()) {
            aggregatedTasks.put(task.getName(), task.getPath());
        }
    }

    private List<DefaultGradleTask> convertTasks(Iterable<? extends GradleTask> tasks) {
        List<DefaultGradleTask> result = Lists.newArrayList();
        for (GradleTask task : tasks) {
            result.add(new DefaultGradleTask()
                    .setName(task.getName())
                    .setPath(task.getPath())
                    .setDescription(task.getDescription()));
        }
        return result;
    }
}
