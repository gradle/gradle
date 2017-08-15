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

package org.gradle.integtests.tooling.r42;

import org.gradle.api.Action;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;

public class NullParameterAction implements BuildAction<CustomModel> {
    private final boolean parameterTypeNull;
    private final boolean parameterInitializerNull;

    public NullParameterAction(boolean parameterTypeNull, boolean parameterInitializerNull) {
        this.parameterTypeNull = parameterTypeNull;
        this.parameterInitializerNull = parameterInitializerNull;
    }

    @Override
    public CustomModel execute(BuildController controller) {
        Class<CustomParameter> parameterType = parameterTypeNull ? null : CustomParameter.class;
        Action<CustomParameter> parameterInitializer = parameterInitializerNull ? null : new Action<CustomParameter>() {
            @Override
            public void execute(CustomParameter customParameter) {}
        };
        return controller.getModel(CustomModel.class, parameterType, parameterInitializer);
    }
}
