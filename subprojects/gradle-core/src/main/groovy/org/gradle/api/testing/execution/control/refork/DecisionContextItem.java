package org.gradle.api.testing.execution.control.refork;

/**
 * Defines a criteria that can be used to determine when a test fork should be restarted.
 *
 * @author Tom Eyckmans
 */
public interface DecisionContextItem {
    /**
     * Defines a unique id to identify the criteria.
     *
     * @return Key of the criteria.
     */
    DecisionContextItemKey getKey();

    /**
     * Returns an instance of the data gatherer that needs to be used for this criteria.
     * <p/>
     * Operates inside the test fork.
     *
     * @return A data gatherer instance.
     */
    DecisionContextItemDataGatherer getDataGatherer();

    /**
     * Returns an instance of the data processor that needs to be used for this criteria.
     * <p/>
     * Operates inside the test server.
     *
     * @return A data processor instance.
     */
    DecisionContextItemDataProcessor getDataProcessor();

    /**
     * Returns an instance of the configuration class used by this criteria.
     * <p/>
     * Instanciated on the test server, used inside the test fork.
     *
     * @return A config class instance. Null in case no configuration is needed.
     */
    DecisionContextItemConfig getConfig();
}
