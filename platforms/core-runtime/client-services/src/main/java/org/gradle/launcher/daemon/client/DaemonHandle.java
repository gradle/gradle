/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.client;

import org.gradle.launcher.daemon.startup.DaemonStartupInfo;
import org.jspecify.annotations.NullMarked;

import java.util.concurrent.TimeUnit;

/**
 * A handle to a daemon started by this process.
 * <p>
 * The {@link DaemonStarter} that creates the handle is only responsible for starting the daemon.
 * The lifetime of the started daemon belongs to the caller, who may use this handle to interact
 * with it.
 */
@NullMarked
public interface DaemonHandle {

    /**
     * The startup information of the daemon, as reported during the startup handshake.
     */
    DaemonStartupInfo getStartupInfo();

    /**
     * Blocks until all resources borrowed from this process are released, or
     * the timeout elapses. Does not await termination of a forked daemon,
     * as it is detached from this process at startup and borrows nothing from it.
     *
     * @return true if resources have been released before timeout, false if the timeout elapsed.
     */
    boolean awaitTermination(long timeout, TimeUnit unit);

}
