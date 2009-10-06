package org.gradle.api.testing.execution.control.refork;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestsExecutedByForkItem implements DecisionContextItem {

    public DecisionContextItemKey getKey() {
        return DecisionContextItemKeys.AMOUNT_OF_TEST_EXECUTED_BY_FORK;
    }

    public DecisionContextItemDataGatherer getDataGatherer() {
        return new AmountOfTestsExecutedByForkDataGatherer();
    }

    public DecisionContextItemDataProcessor getDataProcessor() {
        return new AmountOfTestsExecutedByForkDataProcessor();
    }

    public DecisionContextItemConfig getConfig() {
        return null;
    }
}
