/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.redirector;

import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.time.Clock;
import org.jspecify.annotations.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TestOutputRedirector {
    private final StandardOutputRedirector redirector;
    // Bounded leak, also cleaned up when test worker exits.
    @SuppressWarnings("ThreadLocalUsage")
    private final ThreadLocal<Object> threadOwner = new ThreadLocal<>();
    private final Set<Object> activeOwners = ConcurrentHashMap.newKeySet();
    private volatile @Nullable Object lastOwner;
    final Forwarder outForwarder;
    final Forwarder errForwarder;

    public TestOutputRedirector(Clock clock, TestResultProcessor processor, StandardOutputRedirector redirector) {
        this.redirector = redirector;
        this.outForwarder = new Forwarder(clock, processor, TestOutputEvent.Destination.StdOut);
        this.errForwarder = new Forwarder(clock, processor, TestOutputEvent.Destination.StdErr);
    }

    public void startRedirecting() {
        assert lastOwner != null;

        redirector.redirectStandardOutputTo(outForwarder);
        redirector.redirectStandardErrorTo(errForwarder);
        redirector.start();
    }

    public void stopRedirecting() {
        try {
            redirector.stop();
        } finally {
            activeOwners.clear();
            threadOwner.remove();
            lastOwner = null;
        }
    }

    public void testStarted(Object testId) {
        assert testId != null;
        flushCurrentThread();
        activeOwners.add(testId);
        threadOwner.set(testId);
        lastOwner = testId;
    }

    public void testCompleted(Object testId, Object newOwner) {
        assert newOwner != null;
        flushCurrentThread();
        activeOwners.remove(testId);
        threadOwner.set(newOwner);
        lastOwner = newOwner;
    }

    @Nullable
    Object currentOwner() {
        Object owner = threadOwner.get();
        if (owner != null) {
            if (activeOwners.contains(owner)) {
                return owner;
            }
            // Clear thread owner if no longer active, for GC
            threadOwner.remove();
        }
        // Fallback to last owner if no active owner on current thread
        // This is necessary instead of attributing to the root for compatibility
        // with how JUnit 4 and TestNG handle their test timeout mechanisms.
        return lastOwner;
    }

    private static void flushCurrentThread() {
        if (System.out != null) {
            System.out.flush();
        }
        if (System.err != null) {
            System.err.flush();
        }
    }

    class Forwarder implements StandardOutputRedirector.OutputListener {
        final Clock clock;
        final TestResultProcessor processor;
        final TestOutputEvent.Destination dest;

        Forwarder(Clock clock, TestResultProcessor processor, TestOutputEvent.Destination dest) {
            this.clock = clock;
            this.processor = processor;
            this.dest = dest;
        }

        @Override
        public void onOutput(CharSequence output) {
            Object owner = currentOwner();
            if (owner == null) {
                throw new RuntimeException("Unable send output event from test executor. Please report this problem. Destination: " + dest + ", event: " + output.toString());
            }
            processor.output(owner, new DefaultTestOutputEvent(clock.getCurrentTime(), dest, output.toString()));
        }
    }
}
