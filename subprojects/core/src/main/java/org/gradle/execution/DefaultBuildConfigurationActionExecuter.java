/*
 * Copyright 2015 the original author or authors.
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

import com.google.common.collect.Lists;
import org.gradle.api.internal.GradleInternal;
import org.gradle.execution.plan.ExecutionPlan;

import java.util.List;

public class DefaultBuildConfigurationActionExecuter implements BuildConfigurationActionExecuter {
    private List<? extends BuildConfigurationAction> taskSelectors;

    public DefaultBuildConfigurationActionExecuter(Iterable<? extends BuildConfigurationAction> defaultTaskSelectors) {
        this.taskSelectors = Lists.newArrayList(defaultTaskSelectors);
    }

    @Override
    public void select(GradleInternal gradle, ExecutionPlan plan) {
        // We know that we're running single-threaded here, so we can use coarse grained locks
        gradle.getOwner().getProjects().withMutableStateOfAllProjects(() -> {
            configure(taskSelectors, gradle, plan, 0);
        });
    }

    @Override
    public void setTaskSelectors(List<? extends BuildConfigurationAction> taskSelectors) {
        this.taskSelectors = taskSelectors;
    }

    private void configure(final List<? extends BuildConfigurationAction> processingConfigurationActions, final GradleInternal gradle, final ExecutionPlan plan, final int index) {
        if (index >= processingConfigurationActions.size()) {
            return;
        }
        processingConfigurationActions.get(index).configure(new BuildExecutionContext() {
            @Override
            public GradleInternal getGradle() {
                return gradle;
            }

            @Override
            public ExecutionPlan getExecutionPlan() {
                return plan;
            }

            @Override
            public void proceed() {
                configure(processingConfigurationActions, gradle, plan, index + 1);
            }

        });
    }
}
