/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.Task;
import org.gradle.util.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @author Hans Dockter
 */
public class BuildExecuter {
    private static Logger logger = LoggerFactory.getLogger(BuildExecuter.class);

    private Dag dag;

    public BuildExecuter(Dag dag) {
        this.dag = dag;
    }

    public boolean execute(Iterable<? extends Task> tasks) {
        assert tasks != null;

        Clock clock = new Clock();
        dag.reset();
        fillDag(this.dag, tasks);
        logger.debug("Timing: Creating the DAG took " + clock.getTime());
        clock.reset();
        boolean dagNeutral = dag.execute();
        logger.debug("Timing: Executing the DAG took " + clock.getTime());
        return !dagNeutral;
    }

    private void fillDag(Dag dag, Iterable<? extends Task> tasks) {
        for (Task task : tasks) {
            logger.debug("Find dependsOn tasks for {}", task);
            Set<? extends Task> dependsOnTasks = task.getTaskDependencies().getDependencies(task);
            dag.addTask(task, dependsOnTasks);
            if (dependsOnTasks.size() > 0) {
                logger.debug("Found dependsOn tasks for {}: {}", task, dependsOnTasks);
                fillDag(dag, dependsOnTasks);
            } else {
                logger.debug("Found no dependsOn tasks for {}", task);
            }
        }
    }
}
