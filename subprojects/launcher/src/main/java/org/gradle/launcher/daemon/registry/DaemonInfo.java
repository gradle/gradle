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

package org.gradle.launcher.daemon.registry;

import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonInstanceDetails;
import org.gradle.messaging.remote.Address;

import java.io.Serializable;

/**
 * Provides information about a daemon that is potentially available to do some work.
 */
public class DaemonInfo implements Serializable, DaemonInstanceDetails {

    private final Address address;
    private final DaemonContext context;
    private final String password;
    private boolean idle;

    public DaemonInfo(Address address, DaemonContext context, String password, boolean idle) {
        this.address = address;
        this.context = context;
        this.password = password;
        this.idle = idle;
    }

    public DaemonInfo setIdle(boolean idle) {
        this.idle = idle;
        return this;
    }

    public String getUid() {
        return context.getUid();
    }

    public Long getPid() {
        return context.getPid();
    }

    public Address getAddress() {
        return address;
    }

    public DaemonContext getContext() {
        return context;
    }

    public boolean isIdle() {
        return idle;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return String.format("DaemonInfo{pid=%s, address=%s, idle=%s, context=%s}", context.getPid(), address, idle, context);
    }

}
