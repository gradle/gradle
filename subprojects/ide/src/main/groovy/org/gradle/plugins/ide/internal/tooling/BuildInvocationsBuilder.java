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

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.tooling.internal.gradle.DefaultBuildInvocations;
import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.model.TaskSelector;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.util.List;
import java.util.Set;

public class BuildInvocationsBuilder implements ToolingModelBuilder{
    public boolean canBuild(String modelName) {
        return modelName.equals("org.gradle.tooling.model.gradle.BuildInvocations");
    }

    public DefaultBuildInvocations buildAll(String modelName, Project project) {
        if (!canBuild(modelName)) {
            throw new GradleException("Unknown model name " + modelName);
        }
        Set<String> taskSelectorNames = Sets.newHashSet();
        for (Project child : project.getSubprojects()) {
            DefaultBuildInvocations childBuildInvocations = buildAll(modelName, child);
            for (TaskSelector ts : childBuildInvocations.getTaskSelectors()) {
                taskSelectorNames.add(ts.getName());
            }
            taskSelectorNames.addAll(child.getTasks().getNames());
        }
        List<DefaultGradleTaskSelector> selectors = Lists.newArrayList();
        for (String selectorName : taskSelectorNames) {
            selectors.add(new DefaultGradleTaskSelector().
                    setName(selectorName).
                    setProjectDir(project.getProjectDir()).
                    setDisplayName(String.format("%s built in %s and subprojects.", selectorName, project.getName())));
        }
        return new DefaultBuildInvocations().setSelectors(selectors);
    }
}
