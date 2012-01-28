/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.Factory;
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry;
import org.gradle.launcher.daemon.server.Daemon;

class EmbeddedDaemonStarter implements Runnable {

    private final EmbeddedDaemonRegistry daemonRegistry;
    private final Factory<Daemon> daemonFactory;

    public EmbeddedDaemonStarter(EmbeddedDaemonRegistry daemonRegistry, Factory<Daemon> daemonFactory) {
        this.daemonRegistry = daemonRegistry;
        this.daemonFactory = daemonFactory;
    }

    public void run() {
        daemonRegistry.startDaemon(daemonFactory.create());
    }
}