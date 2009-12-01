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

import java.util.*;

/**
 * @author Tom Eyckmans
 */
public class DefaultMomentDataGatherers {

    private final Map<DataGatherMoment, List<ReforkReasonDataGatherer>> momentDataGatherers;

    public DefaultMomentDataGatherers() {
        momentDataGatherers = new HashMap<DataGatherMoment, List<ReforkReasonDataGatherer>>();
    }

    public void initialize()
    {
        for ( final DataGatherMoment dataGatherMoment : DataGatherMoment.values() ) {
            momentDataGatherers.put(dataGatherMoment, new ArrayList<ReforkReasonDataGatherer>());
        }
    }

    public void addDataGatherer(ReforkReasonDataGatherer dataGatherer)
    {
        if ( dataGatherer == null ) { throw new IllegalArgumentException("dataGatherer can't be null!"); }

        final List<DataGatherMoment> dataGatherMoments = dataGatherer.getDataGatherMoments();
        
        if ( dataGatherMoments != null && !dataGatherMoments.isEmpty() ) {
            for ( final DataGatherMoment dataGatherMoment : dataGatherMoments ) {
                final List<ReforkReasonDataGatherer> momentDataGatherers = this.momentDataGatherers.get(dataGatherMoment);

                momentDataGatherers.add(dataGatherer);

                this.momentDataGatherers.put(dataGatherMoment, momentDataGatherers);
            }
        }
        // no data gather moments - don't add the data gatherer
    }

    public List<ReforkReasonDataGatherer> getDataGatherers(DataGatherMoment dataGatherMoment)
    {
        if ( dataGatherMoment == null ) { throw new IllegalArgumentException("dataGatherMoment can't be null!"); }

        return Collections.unmodifiableList(momentDataGatherers.get(dataGatherMoment));
    }
}
