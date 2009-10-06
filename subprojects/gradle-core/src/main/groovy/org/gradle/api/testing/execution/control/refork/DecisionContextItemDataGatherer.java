package org.gradle.api.testing.execution.control.refork;

import java.util.List;

/**
 * A decision context item data gatherer operates within the test fork process.
 * <p/>
 * The test fork will call {@see processDataGatherMoment} for each data gather moment returned by
 * {@see getDataGatherMoments}.
 *
 * @author Tom Eyckmans
 */
public interface DecisionContextItemDataGatherer {
    DecisionContextItemKey getItemKey();

    /**
     * Allows the data gatherer to initialize itself.
     *
     * @param config context item configuration.
     */
    void configure(DecisionContextItemConfig config);

    /**
     * Defines the moments when this data gatherer needs to be notified.
     * <p/>
     * Note: Only called once at the start of the fork (should always return the same list).
     *
     * @return List of data gather moments.
     */
    List<DataGatherMoment> getDataGatherMoments();

    /**
     * Called for each data gather moment specified by {@see getDataGatherMoments}.
     *
     * @param currentMoment The current data gather moment.
     * @param momentData    Variable size array of Objects, the amount of data depends on the data gather moment.
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
