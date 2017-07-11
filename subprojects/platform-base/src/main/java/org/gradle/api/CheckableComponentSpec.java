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

package org.gradle.api;

import org.gradle.platform.base.ComponentSpec;

import javax.annotation.Nullable;

/**
 * A {@link ComponentSpec} that is directly checkable via a specified task.
 */
@Incubating
public interface CheckableComponentSpec extends ComponentSpec {

    /**
     * Returns the task responsible for checking this component.
     */
    @Nullable
    Task getCheckTask();

    /**
     * Specifies the task responsible for checking this component.
     */
    void setCheckTask(Task checkTask);

    /**
     * Adds tasks required to check this component. Tasks added this way are subsequently
     * added as dependencies of this component's {@link #getCheckTask() check task}.
     */
    void checkedBy(Object... tasks);

}
