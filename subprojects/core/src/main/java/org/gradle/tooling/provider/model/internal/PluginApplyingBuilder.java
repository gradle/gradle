/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.provider.model.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.Project;
import org.gradle.tooling.provider.model.ParameterizedToolingModelBuilder;

@NonNullApi
public class PluginApplyingBuilder implements ParameterizedToolingModelBuilder<PluginApplyingParameter> {

    public static final String MODEL_NAME = PluginApplyingBuilder.class.getName() + "$sideEffect";

    @Override
    public Class<PluginApplyingParameter> getParameterType() {
        return PluginApplyingParameter.class;
    }

    @Override
    public boolean canBuild(String modelName) {
        return modelName.equals(MODEL_NAME);
    }

    @Override
    public Object buildAll(String modelName, PluginApplyingParameter parameter, Project project) {
        project.getPluginManager().apply(parameter.getPluginType());
        return true;
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        // Do nothing, which still has a side effect of the target project being configured
        return true;
    }
}
