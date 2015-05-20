/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.filewatch;

import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class IdleTimeout implements Stoppable {

    private final Control control;
    private final Runnable onTimeOut;

    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicLong lastActivityAt = new AtomicLong(-1);

    public interface Control {
        // expected to block
        boolean await(long lastActivityAt) throws InterruptedException;
    }

    public static class Timeout implements Control {

        private final long timeoutMillis;
        private final Factory<Long> nowFactory;

        public Timeout(long timeoutMillis) {
            this(timeoutMillis, new Factory<Long>() {
                @Override
                public Long create() {
                    return System.currentTimeMillis();
                }
            });
        }

        Timeout(long timeoutMillis, Factory<Long> nowFactory) {
            this.timeoutMillis = timeoutMillis;
            this.nowFactory = nowFactory;
        }

        @Override
        public boolean await(long lastActivityAt) throws InterruptedException {
            if (lastActivityAt == -1) {
                Thread.sleep(timeoutMillis);
            } else {
                long sinceActivity = nowFactory.create() - lastActivityAt;
                if (sinceActivity >= timeoutMillis) {
                    return true;
                } else {
                    Thread.sleep(timeoutMillis - sinceActivity);
                }
            }
            return false;
        }
    }

    public IdleTimeout(long timeoutMillis, Runnable onTimeOut) {
        this(new Timeout(timeoutMillis), onTimeOut);
    }

    IdleTimeout(Control control, Runnable onTimeOut) {
        this.control = control;
        this.onTimeOut = onTimeOut;
    }

    public void tick() {
        lastActivityAt.set(System.currentTimeMillis());
    }

    public void await() {
        if (subscribed.compareAndSet(false, true)) {
            while (!stopped.get()) {
                try {
                    if (control.await(lastActivityAt.get())) {
                        if (stopped.compareAndSet(false, true)) {
                            onTimeOut.run();
                        }
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        } else {
            throw new IllegalStateException("idle timeout is already subscribed");
        }
    }

    @Override
    public void stop() {
        stopped.set(true);
    }
}
