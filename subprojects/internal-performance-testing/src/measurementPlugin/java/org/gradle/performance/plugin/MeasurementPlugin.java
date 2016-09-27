/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.plugin;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.invocation.Gradle;

/**
 * Gradle Plugin used in Gradle's internal performance tests to do measurements
 */
public class MeasurementPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        Gradle gradle = project.getGradle();

        final PerformanceCounterMeasurement performanceCounterMeasurement = new PerformanceCounterMeasurement(project.getRootProject().getBuildDir());
        performanceCounterMeasurement.recordStart();

        gradle.addBuildListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                BuildEventTimeStamps.buildFinished(result);
                performanceCounterMeasurement.recordFinish();
                Project rootProject = result.getGradle().getRootProject();
                HeapDumper.handle(rootProject, rootProject.getLogger());
                new HeapMeasurement().handle(rootProject, rootProject.getLogger());
                ExternalResources.printAndResetStats();
            }

        });

        gradle.getTaskGraph().addTaskExecutionGraphListener(new TaskExecutionGraphListener() {
            @Override
            public void graphPopulated(TaskExecutionGraph graph) {
                BuildEventTimeStamps.configurationEvaluated();
            }
        });
    }
}
