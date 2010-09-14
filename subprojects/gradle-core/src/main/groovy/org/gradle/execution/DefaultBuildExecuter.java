/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.execution;

import org.gradle.api.internal.GradleInternal;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;

import java.util.*;

/**
 * <p>The standard {@link BuildExecuter} implementation.</p>
 */
public class DefaultBuildExecuter extends DelegatingBuildExecuter {
    private final Set<String> excludedTaskNames;

    public DefaultBuildExecuter(Collection<String> includedTaskNames, Collection<String> excludedTaskNames) {
        this.excludedTaskNames = new HashSet<String>(excludedTaskNames);
        if (includedTaskNames.isEmpty()) {
            setDelegate(new ProjectDefaultsBuildExecuter());
        } else {
            setDelegate(new TaskNameResolvingBuildExecuter(includedTaskNames));
        }
    }

    @Override
    public void select(GradleInternal gradle) {
        if (!excludedTaskNames.isEmpty()) {
            final Set<Task> excludedTasks = new HashSet<Task>();
            excludedTasks.addAll(TaskNameResolvingBuildExecuter.select(gradle, excludedTaskNames));
            gradle.getTaskGraph().useFilter(new Spec<Task>() {
                public boolean isSatisfiedBy(Task task) {
                    return !excludedTasks.contains(task);
                }
            });
        }

        super.select(gradle);
    }
}
