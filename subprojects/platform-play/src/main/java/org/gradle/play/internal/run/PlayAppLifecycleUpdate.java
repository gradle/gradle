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
import java.net.InetSocketAddress;

public class PlayAppLifecycleUpdate implements Serializable {
    private final InetSocketAddress address;
    private final Exception exception;

    public static PlayAppLifecycleUpdate stopped() {
        return new PlayAppLifecycleUpdate();
    }

    public static PlayAppLifecycleUpdate running(InetSocketAddress address) {
        return new PlayAppLifecycleUpdate(address);
    }

    public static PlayAppLifecycleUpdate failed(Exception exception) {
        return new PlayAppLifecycleUpdate(exception);
    }

    private PlayAppLifecycleUpdate() {
        this.address = null;
        this.exception = null;
    }

    private PlayAppLifecycleUpdate(InetSocketAddress address) {
        this.address = address;
        this.exception = null;
    }

    private PlayAppLifecycleUpdate(Exception exception) {
        this.address = null;
        this.exception = exception;
    }

    public Exception getException() {
        return exception;
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public boolean isRunning() {
        return address!=null && exception == null;
    }

    public boolean isStopped() {
        return address==null && exception == null;
    }

    public boolean isFailed() {
        return exception != null;
    }
}
