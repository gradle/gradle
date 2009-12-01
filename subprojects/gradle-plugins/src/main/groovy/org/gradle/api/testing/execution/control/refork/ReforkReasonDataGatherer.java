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
 * A decision context item data gatherer operates within the test fork process. <p/> The test fork will call {@link
 * #processDataGatherMoment} for each data gather moment returned by {@link #getDataGatherMoments}.
 *
 * @author Tom Eyckmans
 */
public interface ReforkReasonDataGatherer {
    ReforkReasonKey getKey();

    /**
     * Allows the data gatherer to initialize itself.
     *
     * @param config context item configuration.
     */
    void configure(ReforkReasonConfig config);

    /**
     * Defines the moments when this data gatherer needs to be notified. <p/> Note: Only called once at the start of the
     * fork (should always return the same list).
     *
     * @return List of data gather moments.
     */
    List<DataGatherMoment> getDataGatherMoments();

    /**
     * Called for each data gather moment specified by {@link #getDataGatherMoments}.
     *
     * @param currentMoment The current data gather moment.
     * @param momentData Variable size array of Objects, the amount of data depends on the data gather moment.
     * @return Whether this data gatherer wants to send data back to the test server.
     */
    boolean processDataGatherMoment(DataGatherMoment currentMoment, Object... momentData);

    /**
     * Called when data needs to be reported back to the server process.
     *
     * @return The current data gathered.
     */
    Object getCurrentData();
}
