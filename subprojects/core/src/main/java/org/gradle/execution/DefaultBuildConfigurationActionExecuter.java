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
import org.gradle.util.CollectionUtils;

import java.util.List;

public class DefaultBuildConfigurationActionExecuter implements BuildConfigurationActionExecuter {
    private final List<BuildConfigurationAction> configurationActions;
    private List<? extends BuildConfigurationAction> taskSelectors;

    public DefaultBuildConfigurationActionExecuter(Iterable<? extends BuildConfigurationAction> configurationActions, Iterable<? extends BuildConfigurationAction> defaultTaskSelectors) {
        this.taskSelectors = Lists.newArrayList(defaultTaskSelectors);
        this.configurationActions = Lists.newArrayList(configurationActions);
    }

    public void select(GradleInternal gradle) {
        List<BuildConfigurationAction> processingBuildActions = CollectionUtils.flattenCollections(BuildConfigurationAction.class, configurationActions, taskSelectors);
        configure(processingBuildActions, gradle, 0);
    }

    @Override
    public void setTaskSelectors(List<? extends BuildConfigurationAction> taskSelectors) {
        this.taskSelectors = taskSelectors;
    }

    private void configure(final List<BuildConfigurationAction> processingConfigurationActions, final GradleInternal gradle, final int index) {
        if (index >= processingConfigurationActions.size()) {
            return;
        }
        processingConfigurationActions.get(index).configure(new BuildExecutionContext() {
            public GradleInternal getGradle() {
                return gradle;
            }

            public void proceed() {
                configure(processingConfigurationActions, gradle, index + 1);
            }

        });
    }
}
