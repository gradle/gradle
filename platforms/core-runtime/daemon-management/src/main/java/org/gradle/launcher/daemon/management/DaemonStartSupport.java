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

package org.gradle.launcher.daemon.management;

/**
 * Starts a daemon of the Gradle version this library ships with.
 *
 * <p>Starting a daemon means resolving a distribution, choosing a JVM and launching a process, all of
 * which belong to the Gradle client rather than here. Managing daemons has to work without any of that,
 * so the ability to start one is supplied from outside instead of being built in.
 */
public interface DaemonStartSupport {

    /**
     * Starts an idle daemon and returns its uid, by which it can then be found in the index.
     */
    String startDaemon();
}
