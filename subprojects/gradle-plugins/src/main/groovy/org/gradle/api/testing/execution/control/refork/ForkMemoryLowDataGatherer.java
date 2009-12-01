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

import org.gradle.util.JavaLangRuntimeAdapter;
import org.gradle.util.DefaultJavaLangRuntimeAdapter;

import java.util.List;
import java.util.Arrays;

/**
 * @author Tom Eyckmans
 */
public class ForkMemoryLowDataGatherer extends ReforkReasonKeyLink implements ReforkReasonDataGatherer{

    private static final List<DataGatherMoment> DATA_GATHER_MOMENTS = Arrays.asList(DataGatherMoment.AFTER_TEST_EXECUTION);

    private double memoryLowThreshold;
    private ForkMemoryLowData currentData;
    private JavaLangRuntimeAdapter runtimeAdapter;

    protected ForkMemoryLowDataGatherer(ReforkReasonKey reforkReasonKey) {
        super(reforkReasonKey);
        currentData = new ForkMemoryLowData();
        runtimeAdapter = new DefaultJavaLangRuntimeAdapter();
    }

    public void configure(ReforkReasonConfig config) {
        if ( config == null ) {
            throw new IllegalArgumentException("config can't be null!");
        }
        final ForkMemoryLowConfig typedConfig = (ForkMemoryLowConfig) config;

        memoryLowThreshold = typedConfig.getMemoryLowThreshold();
    }

    public List<DataGatherMoment> getDataGatherMoments() {
        return DATA_GATHER_MOMENTS;
    }

    public boolean processDataGatherMoment(DataGatherMoment currentMoment, Object... momentData) {
        boolean dataSendNeeded = false;

        if ( memoryLowThreshold > 0 ) {
            currentData.setFreeMemory(runtimeAdapter.getFreeMemory());
            currentData.setMaxMemory(runtimeAdapter.getMaxMemory());
            currentData.setTotalMemory(runtimeAdapter.getTotalMemory());

            dataSendNeeded = currentData.getCurrentUsagePercentage() > memoryLowThreshold;
        }

        return dataSendNeeded;
    }

    public Object getCurrentData() {
        return currentData;
    }

    /*
    From this point on methods are for test purposes only.
     */
    double getMemoryLowThreshold() {
        return memoryLowThreshold;
    }

    void setRuntimeAdapter(JavaLangRuntimeAdapter runtimeAdapter) {
        this.runtimeAdapter = runtimeAdapter;
    }
}
