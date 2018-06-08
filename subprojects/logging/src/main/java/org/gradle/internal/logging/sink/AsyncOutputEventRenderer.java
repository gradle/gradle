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

package org.gradle.internal.logging.sink;

import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.logging.dispatch.AsynchronousLogDispatcher;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.time.Clock;

public class AsyncOutputEventRenderer extends OutputEventRenderer implements Stoppable {
    private final AsynchronousLogDispatcher eventQueue;

    public AsyncOutputEventRenderer(Clock clock) {
        super(clock);
        this.eventQueue = new AsynchronousOutputEventQueue("Logging output event processor");
        eventQueue.start();
    }

    @Override
    public void onOutput(OutputEvent event) {
        eventQueue.submit(event);
    }

    @Override
    public void stop() {
        eventQueue.waitForCompletion();
    }

    @Override
    public void flush() {
        eventQueue.flush();
        super.flush();
    }

    private class AsynchronousOutputEventQueue extends AsynchronousLogDispatcher {
        AsynchronousOutputEventQueue(String name) {
            super(name);
        }

        @Override
        public void processEvent(OutputEvent event) {
            AsyncOutputEventRenderer.super.onOutput(event);
        }
    }
}
