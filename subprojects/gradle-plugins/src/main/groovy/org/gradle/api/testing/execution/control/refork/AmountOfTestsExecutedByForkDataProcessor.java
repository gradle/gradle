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

import org.gradle.api.Project;
import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.tasks.testing.NativeTest;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * @author Tom Eyckmans
 */
public class AmountOfTestsExecutedByForkDataProcessor implements ReforkReasonDataProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AmountOfTestsExecutedByForkDataProcessor.class);

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
    public boolean determineReforkNeeded(Pipeline pipeline, int forkId, Object decisionContextItemData) {
        final Long amountOfTestsExecutedByFork = (Long) decisionContextItemData;

        final boolean restartNeeded = amountOfTestsExecutedByFork % reforkEveryThisAmountOfTests == 0;

        if ( restartNeeded ) {
            logger.info("pipeline {}, fork {} : restart needed, amount of tests executed = {}",
                    new Object[]{
                            pipeline.getName(),
                            forkId,
                            amountOfTestsExecutedByFork
                    });
        }

        return restartNeeded;
    }
}
