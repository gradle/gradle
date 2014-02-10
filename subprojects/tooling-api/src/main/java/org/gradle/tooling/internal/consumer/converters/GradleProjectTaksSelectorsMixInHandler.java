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

package org.gradle.tooling.internal.consumer.converters;

import org.gradle.tooling.internal.gradle.DefaultGradleTaskSelector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.GradleTask;

import java.util.HashSet;
import java.util.Set;

public class GradleProjectTaksSelectorsMixInHandler {

    private final GradleProject project;

    public GradleProjectTaksSelectorsMixInHandler(GradleProject project) {
        this.project = project;
    }

    public Set<DefaultGradleTaskSelector> getTaskSelectors() {
        Set<DefaultGradleTaskSelector> taskSelectors = new HashSet<DefaultGradleTaskSelector>();
        for (GradleProject child : project.getChildren()) {
            taskSelectors.addAll(new GradleProjectTaksSelectorsMixInHandler(child).getTaskSelectors());
            for (GradleTask t : child.getTasks()) {
                taskSelectors.add(new DefaultGradleTaskSelector().setName(t.getName()));
            }
        }
        return taskSelectors;
    }
}