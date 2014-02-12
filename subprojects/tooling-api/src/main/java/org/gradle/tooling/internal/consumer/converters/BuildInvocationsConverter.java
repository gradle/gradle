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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;
import org.gradle.tooling.model.TaskSelector;

import java.util.List;
import java.util.Set;

public class BuildInvocationsConverter {
    public DefaultBuildInvocations convert(GradleProject project) {
        return new DefaultBuildInvocations().setSelectors(buildRecursively(project));
    }

    private List<DefaultGradleTaskSelector> buildRecursively(GradleProject project) {
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        selectors.addAll(buildAll(project).getTaskSelectors());
        for (GradleProject childProject : project.getChildren()) {
            selectors.addAll(buildRecursively(childProject));
        }
        return selectors;
    }
    private DefaultBuildInvocations buildAll(GradleProject project) {
        Set<String> taskSelectorNames = Sets.newHashSet();
        for (GradleProject child : project.getChildren()) {
            DefaultBuildInvocations childBuildInvocations = buildAll(child);
            for (TaskSelector ts : childBuildInvocations.getTaskSelectors()) {
                taskSelectorNames.add(ts.getName());
            }
            for (GradleTask task : child.getTasks()) {
                taskSelectorNames.add(task.getName());
            }
        }
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        for (String selectorName : taskSelectorNames) {
            selectors.add(new DefaultGradleTaskSelector().
                    setName(selectorName).
                    setProjectDir(project.getBuildScript().getSourceFile().getParentFile()).
                    setDisplayName(String.format("%s built in %s and subprojects.", selectorName, project.getName())));
        }
        return new DefaultBuildInvocations().setSelectors(selectors);
    }
}
