/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.work.ConditionalExecutionQueue;
import org.gradle.internal.work.ConditionalExecutionQueueFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class WorkerExecutionQueueFactory implements Factory<ConditionalExecutionQueue<DefaultWorkResult>>, Stoppable {
    public static final String QUEUE_DISPLAY_NAME = "WorkerExecutor Queue";
    private final ConditionalExecutionQueueFactory conditionalExecutionQueueFactory;
    private ConditionalExecutionQueue<DefaultWorkResult> queue;

    public WorkerExecutionQueueFactory(ConditionalExecutionQueueFactory conditionalExecutionQueueFactory) {
        this.conditionalExecutionQueueFactory = conditionalExecutionQueueFactory;
    }

    @Nullable
    @Override
    public synchronized ConditionalExecutionQueue<DefaultWorkResult> create() {
        if (queue == null) {
            queue = conditionalExecutionQueueFactory.create(QUEUE_DISPLAY_NAME, DefaultWorkResult.class);
        }
        return queue;
    }

    @Override
    public synchronized void stop() {
        if (queue != null) {
            queue.stop();
        }
    }
}
