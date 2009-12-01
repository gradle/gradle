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

/**
 * @author Tom Eyckmans
 */
public class DefaultDataGatherControlGatherer {
    public void gatherData(DataGatherControl dataGatherControl, ReforkContextData reforkContextData, DataGatherMoment moment, Object... momentData) {
        if ( dataGatherControl == null ) { throw new IllegalArgumentException("dataGatherControl can't be null!"); }
        if ( reforkContextData == null ) { throw new IllegalArgumentException("reforkNeededContext can't be null!"); }
        if ( moment == null ) { throw new IllegalArgumentException("moment can't be null!"); }
        // moment data can be null

        final List<ReforkReasonDataGatherer> currentMomentDataGatherers = dataGatherControl.getDataGatherers(moment);

        for (final ReforkReasonDataGatherer dataGatherer : currentMomentDataGatherers) {
            try {
                final boolean dataSendNeeded = dataGatherer.processDataGatherMoment(moment, momentData);

                if (dataSendNeeded) {
                    reforkContextData.addReasonData(dataGatherer.getKey(), dataGatherer.getCurrentData());
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }
}
