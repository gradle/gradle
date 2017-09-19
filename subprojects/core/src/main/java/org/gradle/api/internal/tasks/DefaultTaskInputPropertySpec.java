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

package org.gradle.api.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.TaskInputPropertyBuilder;
import org.gradle.api.tasks.TaskInputs;
import org.gradle.util.DeprecationLogger;

@NonNullApi
public class DefaultTaskInputPropertySpec extends AbstractTaskInputsDeprecatingTaskPropertyBuilder implements DeclaredTaskInputProperty {

    private final TaskInputs taskInputs;
    private final ValidatingValue value;
    private boolean optional;

    public DefaultTaskInputPropertySpec(TaskInputs taskInputs, String name, ValidatingValue value) {
        this.taskInputs = taskInputs;
        setPropertyNameWithoutValidation(name);
        this.value = value;
        this.optional = true;
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public TaskInputPropertyBuilder optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public void validate(TaskValidationContext context) {
        value.validate(getPropertyName(), optional, ValidationAction.NO_OP, context);
    }

    @Override
    protected TaskInputs getTaskInputs(String method) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("chaining of the " + method, String.format("Use '%s' on TaskInputs directly instead.", method));
        return taskInputs;
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }
}
