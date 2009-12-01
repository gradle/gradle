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
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataProcessor extends ReforkReasonKeyLink implements ReforkReasonDataProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(ForkMemoryLowDataProcessor.class);

    private double memoryLowThreshold = -1;

    public ForkMemoryLowDataProcessor(ReforkReasonKey key) {
        super(key);
    }

    public void configure(ReforkReasonConfig config) {
        if ( config == null ) {
            throw new IllegalArgumentException("config can't be null!");
        }
        final ForkMemoryLowConfig typedConfig = (ForkMemoryLowConfig) config;

        memoryLowThreshold = typedConfig.getMemoryLowThreshold();
    }

    public boolean determineReforkNeeded(Pipeline pipeline, int forkId, Object decisionContextItemData) {
        final ForkMemoryLowData currentData = (ForkMemoryLowData)decisionContextItemData;

        boolean restartNeeded = false;

        if ( memoryLowThreshold > 0 ) {
            final double currentUsagePercentage = currentData.getCurrentUsagePercentage();

            restartNeeded = currentUsagePercentage > memoryLowThreshold;

            if ( restartNeeded ) {
                final String pipelineName = pipeline.getName();

                LOGGER.debug("pipeline {}, fork {} : totalMemory = {}, maxMemory = {}, freeMemory = {}",
                        new Object[]{
                                pipelineName,
                                forkId,
                                currentData.getTotalMemory(),
                                currentData.getMaxMemory(),
                                currentData.getFreeMemory()
                        });
                LOGGER.info("pipeline {}, fork {} : restart needed, memory usage percentage of fork is {}",
                        new Object[]{
                                pipelineName,
                                forkId,
                                currentUsagePercentage
                        });
            }
        }

        return restartNeeded;
    }

    /*
    From this point on methods are for test purposes only.
     */
    double getMemoryLowThreshold() {
        return memoryLowThreshold;
    }
}
