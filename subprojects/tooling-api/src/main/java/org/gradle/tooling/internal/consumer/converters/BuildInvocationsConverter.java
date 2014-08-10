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
import org.gradle.tooling.internal.gradle.ConsumerProvidedTask;
import org.gradle.tooling.internal.gradle.ConsumerProvidedTaskSelector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.List;
import java.util.SortedSet;

public class BuildInvocationsConverter {
    public ConsumerProvidedBuildInvocations convert(GradleProject project) {
        GradleProject rootProject = project;
        while (rootProject.getParent() != null) {
            rootProject = rootProject.getParent();
        }
        List<ConsumerProvidedTaskSelector> selectors = buildRecursively(rootProject);
        return new ConsumerProvidedBuildInvocations(selectors, convertTasks(rootProject.getTasks()));
    }

    private List<ConsumerProvidedTaskSelector> buildRecursively(GradleProject project) {
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();

        collectTasks(project, aggregatedTasks);

        List<ConsumerProvidedTaskSelector> selectors = Lists.newArrayList();
        for (String selectorName : aggregatedTasks.keySet()) {
            SortedSet<String> selectorTasks = Sets.newTreeSet(new TaskNameComparator());
            selectorTasks.addAll(aggregatedTasks.get(selectorName));
            selectors.add(new ConsumerProvidedTaskSelector().
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

    private List<ConsumerProvidedTask> convertTasks(Iterable<? extends GradleTask> tasks) {
        List<ConsumerProvidedTask> result = Lists.newArrayList();
        for (GradleTask task : tasks) {
            result.add(new ConsumerProvidedTask()
                    .setName(task.getName())
                    .setPath(task.getPath())
                    .setDescription(task.getDescription()));
        }
        return result;
    }
}
