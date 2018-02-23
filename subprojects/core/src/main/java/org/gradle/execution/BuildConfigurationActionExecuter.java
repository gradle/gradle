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

import org.gradle.api.internal.GradleInternal;

import java.util.List;


/**
 * Selects the tasks requested for a build.
 */
public interface BuildConfigurationActionExecuter {
    /**
     * Selects the tasks to execute, if any. This method is called before any other methods on this executer.
     */
    void select(GradleInternal gradle);

    /**
     * registers actions allowing late customization of handled BuildConfigurationActions, if any. This method is called before any other methods on this executer.
     */
    void setTaskSelectors(List<? extends BuildConfigurationAction> taskSelectors);
}
