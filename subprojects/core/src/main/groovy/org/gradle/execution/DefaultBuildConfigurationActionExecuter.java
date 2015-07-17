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
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;

import java.util.List;

public class DefaultBuildConfigurationActionExecuter implements BuildConfigurationActionExecuter {
    private final List<BuildConfigurationAction> configurationActions;
    private final List<Transformer<List<BuildConfigurationAction>, List<BuildConfigurationAction>>> configurationActionsTransformations;

    public DefaultBuildConfigurationActionExecuter(Iterable<? extends BuildConfigurationAction> configurationActions) {
        this.configurationActions = Lists.newArrayList(configurationActions);
        this.configurationActionsTransformations = Lists.newArrayList();
    }

    @Override
    public void registerBuildConfigurationTransformer(Transformer<List<BuildConfigurationAction>, List<BuildConfigurationAction>> transformer) {
        configurationActionsTransformations.add(transformer);
    }

    public void select(GradleInternal gradle) {
        List<BuildConfigurationAction> processingBuildActions = configurationActions;
        for (Transformer<List<BuildConfigurationAction>, List<BuildConfigurationAction>> customizationAction : configurationActionsTransformations) {
            processingBuildActions = customizationAction.transform(processingBuildActions);
        }
        configure(processingBuildActions, gradle, 0);
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
