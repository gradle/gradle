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

import org.gradle.api.internal.BuildInternal;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.util.GUtil;

import java.util.*;

/**
 * <p>The standard {@link BuildExecuter} implementation. Parses the set of task names provided on the command-line, and
 * delegates to the appropriate executer based on the result.</p>
 */
public class DefaultBuildExecuter extends DelegatingBuildExecuter {
    private final Set<String> excludedTaskNames = new HashSet<String>();

    public DefaultBuildExecuter(Collection<String> names) {
        List<String> taskNames = new ArrayList<String>();
        for (String taskName : names) {
            if (taskName.startsWith("!")) {
                excludedTaskNames.add(taskName.substring(1));
            } else {
                taskNames.add(taskName);
            }
        }

        if (taskNames.isEmpty()) {
            setDelegate(new ProjectDefaultsBuildExecuter());
        } else {
            setDelegate(new TaskNameResolvingBuildExecuter(taskNames));
        }
    }

    @Override
    public void select(BuildInternal build) {
        if (!excludedTaskNames.isEmpty()) {
            final Set<Task> excludedTasks = new HashSet<Task>();
            GUtil.flatten(TaskNameResolvingBuildExecuter.select(build, excludedTaskNames), excludedTasks);
            build.getTaskGraph().useFilter(new Spec<Task>() {
                public boolean isSatisfiedBy(Task task) {
                    return !excludedTasks.contains(task);
                }
            });
        }

        super.select(build);
    }
}
