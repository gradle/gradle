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

package org.gradle.language.base.internal.tasks;

import com.google.common.collect.Lists;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskAction;
import org.gradle.platform.base.internal.BinarySpecInternal;

import java.util.List;
import java.util.Set;

@Incubating
public class AssembleBinariesTask extends DefaultTask {
    final private List<BinarySpecInternal> notBuildableBinaries = Lists.newArrayList();

    public void notBuildable(BinarySpecInternal binary) {
        notBuildableBinaries.add(binary);
    }

    @TaskAction
    void checkForBuildableBinaries() {
        Set<? extends Task> taskDependencies = getTaskDependencies().getDependencies(this);

        if (taskDependencies.size() == 0 && notBuildableBinaries.size() > 0) {
            throw new GradleException(String.format("No buildable binaries found."));
        }
    }
}
