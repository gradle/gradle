/*
 * Copyright 2009 the original author or authors.
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

/**
 * @author Hans Dockter
 */
public interface OutputHistory {
    /**
     * Returns whether the last execution created successfully the task output.
     * If the last execution failed or when no history information is available (e.g. after a clean),
     * false is returned.
     */
    boolean wasCreatedSuccessfully();

    /**
     * Returns the time of the last time the task output was modified. If the last task execution was not successful,
     * or if there is no history information available (e.g. after a clean), null is returned. Whether the task
     * output is marked as modified or not depends on the value of {@link org.gradle.api.Task#getDidWork()}.
     */
    Long getLastModified();
}
