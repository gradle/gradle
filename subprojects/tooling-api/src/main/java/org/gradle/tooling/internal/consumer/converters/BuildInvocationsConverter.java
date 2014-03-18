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
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.List;

public class BuildInvocationsConverter {
    public DefaultBuildInvocations<GradleTask> convert(GradleProject project) {
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        List<GradleTask> tasks = Lists.newArrayList();
        buildRecursively(project, selectors, tasks);
        return new DefaultBuildInvocations<GradleTask>().setSelectors(selectors).setTasks(tasks);
    }

    private void buildRecursively(GradleProject project, List<DefaultGradleTaskSelector> selectors, List<GradleTask> tasks) {
        for (GradleProject childProject : project.getChildren()) {
            buildRecursively(childProject, selectors, tasks);
        }
        Multimap<String, String> aggregatedTasks = ArrayListMultimap.create();
        for (DefaultGradleTaskSelector childSelector : selectors) {
            aggregatedTasks.putAll(childSelector.getName(), childSelector.getTasks());
        }
        for (GradleTask task : project.getTasks()) {
            aggregatedTasks.put(task.getName(), task.getPath());
            tasks.add(task);
        }
        for (String selectorName : aggregatedTasks.keySet()) {
            selectors.add(new DefaultGradleTaskSelector().
                    setName(selectorName).
                    setTaskNames(Sets.newHashSet(aggregatedTasks.get(selectorName))).
                    setDescription(project.getParent() != null
                            ? String.format("%s:%s task selector", project.getPath(), selectorName)
                            : String.format("%s task selector", selectorName)).
                    setDisplayName(String.format("%s in %s and subprojects.", selectorName, project.getName())));
        }
    }
}
