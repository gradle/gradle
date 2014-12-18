/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.run;

import java.io.Serializable;

public class PlayAppLifecycleUpdate implements Serializable {
    private final boolean running;
    private final Exception exception;

    public static PlayAppLifecycleUpdate stopped() {
        return new PlayAppLifecycleUpdate(false);
    }

    public static PlayAppLifecycleUpdate running() {
        return new PlayAppLifecycleUpdate(true);
    }

    public static PlayAppLifecycleUpdate failed(Exception exception) {
        return new PlayAppLifecycleUpdate(exception);
    }

    private PlayAppLifecycleUpdate(boolean isRunning) {
        this.running = isRunning;
        this.exception = null;
    }

    private PlayAppLifecycleUpdate(Exception exception) {
        this.running = false;
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isRunning() {
        return running && exception == null;
    }

    public boolean isStopped() {
        return !running && exception == null;
    }

    public boolean isFailed() {
        return exception != null;
    }
}
