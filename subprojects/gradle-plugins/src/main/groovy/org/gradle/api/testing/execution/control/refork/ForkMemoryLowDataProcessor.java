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
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.tasks.testing.NativeTest;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataProcessor implements ReforkReasonDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ForkMemoryLowDataProcessor.class);

    private double lowMemoryThreshold = -1;

    public void configure(Project project, NativeTest testTask) {
        lowMemoryThreshold = testTask.getLowMemoryThreshold();
    }

    public boolean determineReforkNeeded(Pipeline pipeline, int forkId, Object decisionContextItemData) {
        final ForkMemoryLowData currentData = (ForkMemoryLowData)decisionContextItemData;

        boolean restartNeeded = false;

        if ( lowMemoryThreshold > 0 ) {
            restartNeeded = currentData.getCurrentUsagePercentage() > lowMemoryThreshold;

            if ( restartNeeded ) {
                logger.debug("pipeline {}, fork {} : totalMemory = {}, maxMemory = {}, freeMemory = {}",
                        new Object[]{
                                pipeline.getName(),
                                forkId,
                                currentData.getTotalMemory(),
                                currentData.getMaxMemory(),
                                currentData.getFreeMemory()
                        });
                logger.info("pipeline {}, fork {} : restart needed, memory usage percentage of fork is {}",
                        new Object[]{
                                pipeline.getName(),
                                forkId,
                                currentData.getCurrentUsagePercentage()
                        });
            }
        }

        return restartNeeded;
    }
}
