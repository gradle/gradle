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
package org.gradle.api.testing.pipelinesplit;

import org.gradle.api.testing.execution.Pipeline;
import org.gradle.api.testing.fabric.TestClassRunInfo;
import org.gradle.api.testing.pipelinesplit.policies.SplitPolicyMatcher;
import org.gradle.util.queues.AbstractBlockingQueueItemConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Tom Eyckmans
 */
public class PipelineSplitWorker extends AbstractBlockingQueueItemConsumer<TestClassRunInfo> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PipelineSplitWorker.class);

    private final TestPipelineSplitOrchestrator splitOrchestrator;
    private final AtomicLong matchCount = new AtomicLong(0);
    private final AtomicLong discardedCount = new AtomicLong(0);
    private final List<SplitPolicyMatcher> splitPolicyMatchers;
    private Map<SplitPolicyMatcher, Pipeline> pipelineMatchers;

    public PipelineSplitWorker(TestPipelineSplitOrchestrator splitOrchestrator,
                               BlockingQueue<TestClassRunInfo> toConsumeQueue, long pollTimeout,
                               TimeUnit pollTimeoutTimeUnit, List<SplitPolicyMatcher> splitPolicyMatchers,
                               Map<SplitPolicyMatcher, Pipeline> pipelineMatchers) {
        super(toConsumeQueue, pollTimeout, pollTimeoutTimeUnit);
        this.splitOrchestrator = splitOrchestrator;
        this.splitPolicyMatchers = splitPolicyMatchers;
        this.pipelineMatchers = pipelineMatchers;
    }

    public void setUp() {
        splitOrchestrator.splitWorkerStarted(this);
    }

    protected boolean consume(TestClassRunInfo queueItem) {
        LOGGER.debug("[pipeline-splitting >> test-run] {}", queueItem.getTestClassName());

        SplitPolicyMatcher matcher = null;

        final Iterator<SplitPolicyMatcher> matcherIterator = splitPolicyMatchers.iterator();
        while (matcher == null && matcherIterator.hasNext()) {
            final SplitPolicyMatcher currentMatcher = matcherIterator.next();
            if (currentMatcher.match(queueItem)) {
                matcher = currentMatcher;
            }
        }

        if (matcher != null) {
            pipelineMatchers.get(matcher).addTestClassRunInfo(queueItem);
            matchCount.incrementAndGet();
        } else {
            discardedCount.incrementAndGet();
        }

        return false; // don't stop
    }

    protected void tearDown() {
        LOGGER.debug("[split-worker-match-count] " + matchCount.get());
        LOGGER.debug("[split-worker-discarded-count] " + discardedCount.get());

        splitOrchestrator.splitWorkerStopped(this);
    }
}
