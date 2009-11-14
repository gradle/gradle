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
package org.gradle.api.testing.execution.control.refork;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.NativeTest;

/**
 * Data processor that determines wether the fork process reporting the data needs to be restarted.
 * <p/>
 * A data processor operates on the test server side.
 *
 * @author Tom Eyckmans
 */
public interface ReforkReasonDataProcessor {

    /**
     * Allow the data processor to perform initialization.
     *
     * @param project  The project the tests are running for.
     * @param testTask The test task that is being executed.
     */
    void configure(Project project, NativeTest testTask);

    /**
     * Process the data and decide wether the fork process needs to be restarted.
     *
     * @param decisionContextItemData The data that needs to be examined.
     * @return Wether or not the fork needs to be restarted. (true = restart needed).
     */
    boolean determineReforkNeeded(Object decisionContextItemData);
}
