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

import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataGatherer implements ReforkReasonDataGatherer{

    private static final List<DataGatherMoment> DATA_GATHER_MOMENTS = Arrays.asList(DataGatherMoment.AFTER_TEST_EXECUTION);

    private double memoryLowThreshold;
    private ForkMemoryLowData currentData = new ForkMemoryLowData();

    public ReforkReasonKey getItemKey() {
        return ReforkReasons.FORK_MEMORY_LOW;
    }

    public void configure(ReforkReasonConfig config) {
        memoryLowThreshold = ((ForkMemoryLowConfig)config).getMemoryLowThreshold();
    }

    public List<DataGatherMoment> getDataGatherMoments() {
        return DATA_GATHER_MOMENTS;
    }

    public boolean processDataGatherMoment(DataGatherMoment currentMoment, Object... momentData) {
        boolean dataSendNeeded = false;

        if ( memoryLowThreshold > 0 ) {
            currentData.setFreeMemory(Runtime.getRuntime().freeMemory());
            currentData.setMaxMemory(Runtime.getRuntime().maxMemory());
            currentData.setTotalMemory(Runtime.getRuntime().totalMemory());

            dataSendNeeded = currentData.getCurrentUsagePercentage() > memoryLowThreshold;
        }

        return dataSendNeeded;
    }

    public Object getCurrentData() {
        return currentData;
    }
}
