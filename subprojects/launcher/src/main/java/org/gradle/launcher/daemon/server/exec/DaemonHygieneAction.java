/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.launcher.daemon.server.exec;

import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;

public class DaemonHygieneAction implements DaemonCommandAction {

    private final long gcDelay;
    private TimeProvider timeProvider;
    private long nextGcHint;

    public DaemonHygieneAction() {
        //by default, don't hint for gc more often than once per 2 minutes
        //because it is a full scan
        this(1000 * 60 * 2, new TrueTimeProvider());
    }

    DaemonHygieneAction(long gcDelay, TimeProvider timeProvider) {
        this.gcDelay = gcDelay;
        this.timeProvider = timeProvider;
    }

    public void execute(DaemonCommandExecution execution) {
        execution.proceed();
        long time = timeProvider.getCurrentTime();
        if (time > nextGcHint) {
            gc();
            nextGcHint = time + gcDelay;
        }
    }

    void gc() {
        System.gc();
    }
}
