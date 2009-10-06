package org.gradle.api.testing.execution.control.refork;

import org.gradle.api.Project;
import org.gradle.api.tasks.testing.NativeTest;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestsExecutedByForkDataProcessor implements DecisionContextItemDataProcessor {

    private long reforkEveryThisAmountOfTests = Long.MAX_VALUE; // Long.MAX_VALUE ~ fork once.

    public void configure(Project project, NativeTest testTask) {
        reforkEveryThisAmountOfTests = testTask.getReforkEvery();
    }

    /**
     * Signals a refork each time a configurable amount of tests is run.
     *
     * @param decisionContextItemData the amount of tests currently executed by the fork.
     * @return true if the fork needs to restart.
     */
    public boolean determineReforkNeeded(Object decisionContextItemData) {
        final Long amountOfTestsExecutedByFork = (Long) decisionContextItemData;

        return amountOfTestsExecutedByFork % reforkEveryThisAmountOfTests == 0;
    }
}
