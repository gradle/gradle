/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.launcher.exec;

import org.gradle.api.internal.tasks.execution.statistics.TaskExecutionStatisticsEventAdapter;
import org.gradle.internal.buildevents.TaskExecutionStatisticsReporter;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.invocation.BuildAction;
import org.gradle.internal.invocation.BuildActionRunner;
import org.gradle.internal.invocation.BuildController;
import org.gradle.internal.logging.text.StyledTextOutputFactory;

public class BuildResultReportingBuildActionRunner implements BuildActionRunner {
    private final BuildActionRunner delegate;
    private final StyledTextOutputFactory styledTextOutputFactory;

    public BuildResultReportingBuildActionRunner(BuildActionRunner delegate, StyledTextOutputFactory styledTextOutputFactory) {
        this.delegate = delegate;
        this.styledTextOutputFactory = styledTextOutputFactory;
    }

    @Override
    public void run(BuildAction action, BuildController buildController) {
        TaskExecutionStatisticsEventAdapter taskStatisticsCollector = new TaskExecutionStatisticsEventAdapter();
        ListenerManager listenerManager = buildController.getGradle().getServices().get(ListenerManager.class);
        listenerManager.addListener(taskStatisticsCollector);
        try {
            delegate.run(action, buildController);
        } finally {
            new TaskExecutionStatisticsReporter(styledTextOutputFactory).buildFinished(taskStatisticsCollector.getStatistics());
        }
    }
}
