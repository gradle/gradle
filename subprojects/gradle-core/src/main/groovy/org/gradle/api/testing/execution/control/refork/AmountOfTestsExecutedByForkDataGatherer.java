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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Data gatherer that counts the amount of tests that have been executed by the fork it is instanciated in.
 *
 * @author Tom Eyckmans
 */
public class AmountOfTestsExecutedByForkDataGatherer implements DecisionContextItemDataGatherer {

    private static final List<DataGatherMoment> dataGatherMoments = Arrays.asList(DataGatherMoment.AFTER_TEST_EXECUTION);

    private AtomicLong amountOfTestsExecutedByFork;

    /**
     * Default constructor.
     */
    public AmountOfTestsExecutedByForkDataGatherer() {
        amountOfTestsExecutedByFork = new AtomicLong(0);
    }

    public DecisionContextItemKey getItemKey() {
        return DecisionContextItemKeys.AMOUNT_OF_TEST_EXECUTED_BY_FORK;
    }

    /**
     * Initialize the amount of tests exected by this fork on zero.
     *
     * @param config Item configuration.
     */
    public void configure(DecisionContextItemConfig config) {
        this.amountOfTestsExecutedByFork = new AtomicLong(0);
    }

    /**
     * This data gatherer needs to be notified when a test is executed.
     *
     * @return Always returns [DataGatherMoment.TEST_EXECUTED]
     */
    public List<DataGatherMoment> getDataGatherMoments() {
        return dataGatherMoments;
    }

    /**
     * Called after a test is exected.
     *
     * @param moment     DataGatherMoment.TEST_EXECUTED
     * @param momentData Variable size array of Objects, the amount of data depends on the data gather moment.
     */
    public boolean processDataGatherMoment(DataGatherMoment moment, Object... momentData) {
        amountOfTestsExecutedByFork.incrementAndGet();

        return true; // report back after each test perhaps a little excessive but ok for now.
    }

    /**
     * @return The current data value.
     */
    public Long getCurrentData() {
        return amountOfTestsExecutedByFork.get();
    }
}
