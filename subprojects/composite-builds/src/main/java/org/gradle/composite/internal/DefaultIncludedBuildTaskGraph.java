/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.composite.internal;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.initialization.IncludedBuildExecuter;
import org.gradle.initialization.IncludedBuildTaskGraph;

import java.util.Collection;

public class DefaultIncludedBuildTaskGraph implements IncludedBuildTaskGraph {
    private final Multimap<BuildIdentifier, String> tasksForBuild = LinkedHashMultimap.create();
    private final IncludedBuildExecuter includedBuildExecuter;

    public DefaultIncludedBuildTaskGraph(IncludedBuildExecuter includedBuildExecuter) {
        this.includedBuildExecuter = includedBuildExecuter;
    }

    @Override
    public synchronized void addTasks(BuildIdentifier requestingBuild, BuildIdentifier targetBuild, Iterable<String> taskNames) {
        tasksForBuild.putAll(targetBuild, taskNames);
    }

    @Override
    public void awaitCompletion(BuildIdentifier requestingBuild, BuildIdentifier targetBuild) {
        includedBuildExecuter.execute(requestingBuild, targetBuild, getTasksForBuild(targetBuild));
    }

    private synchronized Collection<String> getTasksForBuild(BuildIdentifier targetBuild) {
        return tasksForBuild.get(targetBuild);
    }

}
