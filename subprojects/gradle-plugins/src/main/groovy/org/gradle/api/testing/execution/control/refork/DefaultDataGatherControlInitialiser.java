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

import java.util.Map;

/**
 * @author Tom Eyckmans
 */
public class DefaultDataGatherControlInitialiser {

    private ReforkReasonRegisterAdapter reforkReasonRegisterAdapter;

    public DefaultDataGatherControlInitialiser() {
        reforkReasonRegisterAdapter = new DefaultReforkReasonRegisterAdapter();
    }

    public void initialize(DataGatherControl dataGatherControl, ReforkReasonConfigs reforkReasonConfigs)
    {
        if ( dataGatherControl == null ) { throw new IllegalArgumentException("dataGatherControl can't be null!"); }
        if ( reforkReasonConfigs == null ) { throw new IllegalArgumentException("reforkReasonConfigs can't be null!"); }

        final Map<ReforkReasonKey, ReforkReasonConfig> reasonConfigs = reforkReasonConfigs.getConfigs();

        for (final ReforkReasonKey reasonKey : reforkReasonConfigs.getKeys()) {
            final ReforkReason reforkReason = reforkReasonRegisterAdapter.getReforkReason(reasonKey);
            final ReforkReasonDataGatherer dataGatherer = reforkReason.getDataGatherer();

            final ReforkReasonConfig itemConfig = reasonConfigs.get(reasonKey);

            try {
                dataGatherer.configure(itemConfig);

                dataGatherControl.addDataGatherer(dataGatherer);
            }
            catch ( Throwable t ) {
                t.printStackTrace();
            }
        }
    }

    public void setReforkReasonRegisterAdapter(ReforkReasonRegisterAdapter reforkReasonRegisterAdapter) {
        this.reforkReasonRegisterAdapter = reforkReasonRegisterAdapter;
    }
}
