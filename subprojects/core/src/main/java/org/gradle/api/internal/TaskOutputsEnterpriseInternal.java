/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;

/**
 * Develocity specific extensions of {@link TaskOutputsInternal}.
 * <p>
 * This class exists to hide these additional methods from the public API since {@link DefaultTask#getOutputs()}
 * returns {@link TaskOutputsInternal} rather than {@link org.gradle.api.tasks.TaskOutputs}.
 *
 * @since 8.2
 */
@NonNullApi
public interface TaskOutputsEnterpriseInternal extends TaskOutputsInternal {

    /**
     * Whether the task's outputs should be stored in the build cache.
     */
    boolean getStoreInCache();

    /**
     * Avoid storing the task's outputs in the build cache.
     * <p>
     * This does not prevent the task from being up-to-date.
     */
    void doNotStoreInCache();

}
