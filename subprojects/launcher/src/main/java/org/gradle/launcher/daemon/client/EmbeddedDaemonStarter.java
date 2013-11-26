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

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.launcher.daemon.diagnostics.DaemonStartupInfo;
import org.gradle.launcher.daemon.server.Daemon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

class EmbeddedDaemonStarter implements DaemonStarter, Stoppable {
    private final Factory<Daemon> daemonFactory;
    private final List<Daemon> daemons = new ArrayList<Daemon>();
    private final Lock daemonsLock = new ReentrantLock();

    public EmbeddedDaemonStarter(Factory<Daemon> daemonFactory) {
        this.daemonFactory = daemonFactory;
    }

    public DaemonStartupInfo startDaemon() {
        Daemon daemon = daemonFactory.create();
        startDaemon(daemon);
        return new DaemonStartupInfo(daemon.getUid(), null);
    }

    public void startDaemon(Daemon daemon) {
        daemonsLock.lock();
        try {
            daemons.add(daemon);
        } finally {
            daemonsLock.unlock();
        }

        daemon.start();
    }

    public void stop() {
        List<Daemon> daemonsToStop;

        daemonsLock.lock();
        try {
            daemonsToStop = new ArrayList<Daemon>(daemons);
            daemons.clear();
        } finally {
            daemonsLock.unlock();
        }

        CompositeStoppable.stoppable(daemonsToStop).stop();
    }}