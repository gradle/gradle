/*
 * Copyright 2020 the original author or authors.
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


/**
 * Listen for instant execution problems.
 */
public interface InstantExecutionProblemsListener {

    /**
     * Called when accessing the project.
     */
    void onProjectAccess(String invocationDescription, Object invocationSource);

    /**
     * Called when accessing task dependencies.
     */
    void onTaskDependenciesAccess(String invocationDescription, Object invocationSource);
}
