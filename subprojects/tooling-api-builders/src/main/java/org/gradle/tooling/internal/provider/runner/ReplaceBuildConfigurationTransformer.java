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

package org.gradle.tooling.internal.provider.runner;

import com.google.common.collect.Lists;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.execution.BuildConfigurationAction;
import org.gradle.util.CollectionUtils;

import java.util.List;

class ReplaceBuildConfigurationTransformer implements Transformer<List<BuildConfigurationAction>, List<BuildConfigurationAction>> {
    private final List<Class<? extends BuildConfigurationAction>> replaceableActionClasses;

    private BuildConfigurationAction customBuildConfigurationAction;

    public ReplaceBuildConfigurationTransformer(BuildConfigurationAction customBuildConfigurationAction, List<Class<? extends BuildConfigurationAction>> replaceableActionClasses) {
        this.customBuildConfigurationAction = customBuildConfigurationAction;
        this.replaceableActionClasses = Lists.newArrayList(replaceableActionClasses);
    }

    @Override
    public List<BuildConfigurationAction> transform(List<BuildConfigurationAction> givenBuildConfigurationActions) {
        List<BuildConfigurationAction> replacedBuildConfigurations = Lists.newArrayList();
        replacedBuildConfigurations.addAll(CollectionUtils.filter(givenBuildConfigurationActions, new Spec<BuildConfigurationAction>() {
            @Override
            public boolean isSatisfiedBy(final BuildConfigurationAction givenBuildConfiguration) {
                return !CollectionUtils.any(replaceableActionClasses, new Spec<Class<? extends BuildConfigurationAction>>() {
                    @Override
                    public boolean isSatisfiedBy(Class<? extends BuildConfigurationAction> classToBeFiltered) {
                        return classToBeFiltered.isAssignableFrom(givenBuildConfiguration.getClass());
                    }
                });
            };
        }));
        replacedBuildConfigurations.add(customBuildConfigurationAction);
        return replacedBuildConfigurations;
    }
}
