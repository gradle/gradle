/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.tooling.internal.provider.action;

import org.gradle.api.internal.StartParameterInternal;
import org.gradle.internal.invocation.BuildAction;

public class ExecuteBuildAction implements BuildAction {
    private final StartParameterInternal startParameter;

    public ExecuteBuildAction(StartParameterInternal startParameter) {
        this.startParameter = startParameter;
    }

    @Override
    public StartParameterInternal getStartParameter() {
        return startParameter;
    }

    @Override
    public boolean isRunTasks() {
        return true;
    }

    @Override
    public boolean isCreateModel() {
        return false;
    }
}
