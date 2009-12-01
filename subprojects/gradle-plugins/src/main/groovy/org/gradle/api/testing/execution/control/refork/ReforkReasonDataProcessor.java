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

import org.gradle.api.testing.execution.Pipeline;

/**
 * Data processor that determines wether the fork process reporting the data needs to be restarted. <p/> A data
 * processor operates on the test server side.
 *
 * @author Tom Eyckmans
 */
public interface ReforkReasonDataProcessor {

    ReforkReasonKey getKey();

    /**
     * Allow the data processor to perform initialization.
     *
     * @param config context item configuration.
     */
    void configure(ReforkReasonConfig config);

    /**
     * Process the data and decide wether the fork process needs to be restarted.
     *
     * @param pipeline The pipeline refork needed is being checked for.
     * @param forkId The fork id refork needed is being checked for.
     * @param reforkReasonData The data that needs to be examined.
     * @return Wether or not the fork needs to be restarted. (true = restart needed).
     */
    boolean determineReforkNeeded(Pipeline pipeline, int forkId, Object reforkReasonData);
}
