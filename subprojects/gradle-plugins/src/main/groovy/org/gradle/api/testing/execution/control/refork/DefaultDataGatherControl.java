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
public class DefaultDataGatherControl implements DataGatherControl {

    private DefaultMomentDataGatherers momentDataGatherers;
    private DefaultDataGatherControlInitialiser initialiser;
    private DefaultDataGatherControlGatherer gatherer;

    public DefaultDataGatherControl() {
        momentDataGatherers = new DefaultMomentDataGatherers();
        initialiser = new DefaultDataGatherControlInitialiser();
        gatherer = new DefaultDataGatherControlGatherer();
    }

    public void initialize(final ReforkReasonConfigs reforkReasonConfigs) {
        if ( reforkReasonConfigs == null ) { throw new IllegalArgumentException("reforkReasonConfigs can't be null!"); }

        momentDataGatherers.initialize();

        initialiser.initialize(this, reforkReasonConfigs);
    }

    public ReforkContextData gatherData(DataGatherMoment moment, Object... momentData) {
        final ReforkContextData reforkContextData = new DefaultReforkContextData();

        gatherer.gatherData(this, reforkContextData, moment, momentData);

        return reforkContextData;
    }

    public void addDataGatherer(ReforkReasonDataGatherer dataGatherer) {
        momentDataGatherers.addDataGatherer(dataGatherer);
    }

    public List<ReforkReasonDataGatherer> getDataGatherers(DataGatherMoment dataGatherMoment) {
        return momentDataGatherers.getDataGatherers(dataGatherMoment);
    }

    // methods for test purposes

    void setMomentDataGatherers(DefaultMomentDataGatherers momentDataGatherers) {
        this.momentDataGatherers = momentDataGatherers;
    }

    void setInitialiser(DefaultDataGatherControlInitialiser initialiser) {
        this.initialiser = initialiser;
    }

    void setGatherer(DefaultDataGatherControlGatherer gatherer) {
        this.gatherer = gatherer;
    }
}
