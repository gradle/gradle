/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.tasks.diagnostics.internal.result;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;

/**
 * By Szczepan Faber on 7/12/13
 */
public class ResolutionResultKeeper {

    public void setup(Project project, String enablingTask, ResolutionResultConsumer consumer) {
        project.getGradle().getTaskGraph().addTaskExecutionGraphListener(new MaybeConsumeResolutionResult(enablingTask, project, consumer));
    }

    private static class MaybeConsumeResolutionResult implements TaskExecutionGraphListener {
        private String taskPath;
        private Project project;
        private ResolutionResultConsumer consumer;

        public MaybeConsumeResolutionResult(String enablingTask, Project project, ResolutionResultConsumer consumer) {
            this.taskPath = enablingTask;
            this.project = project;
            this.consumer = consumer;
        }

        public void graphPopulated(TaskExecutionGraph graph) {
            if (graph.hasTask(taskPath)) {
                project.getConfigurations().all(new ConsumeResolutionResult(consumer));
            }
            graph.removeTaskExecutionGraphListener(this);
        }
    }

    private static class ConsumeResolutionResult implements Action<Configuration> {
        private final ResolutionResultConsumer consumer;

        public ConsumeResolutionResult(ResolutionResultConsumer consumer) {
            this.consumer = consumer;
        }

        public void execute(final Configuration conf) {
            conf.getIncoming().withResolutionResult(new Action<ResolutionResult>() {
                public void execute(ResolutionResult resolutionResult) {
                    consumer.resolutionResult(conf, resolutionResult);
                }
            });
        }
    }
}
