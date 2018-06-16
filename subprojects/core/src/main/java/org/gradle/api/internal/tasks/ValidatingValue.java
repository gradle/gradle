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

import javax.annotation.Nullable;
import java.util.concurrent.Callable;

public interface ValidatingValue extends Callable<Object>  {
    @Nullable
    @Override
    Object call();

    void validate(String propertyName, boolean optional, ValidationAction valueValidator, TaskValidationContext context);

    /**
     * Called immediately prior to this property being used as a task input.
     * The property implementation may finalize the property value, prevent further changes to the value and enable caching of whatever state it requires to efficiently snapshot and query the input files during task execution.
     */
    void prepareValue();

    /**
     * Called after the completion of the task, regardless of the task outcome. The property implementation can release any state that was cached during task execution.
     */
    void cleanupValue();
}
