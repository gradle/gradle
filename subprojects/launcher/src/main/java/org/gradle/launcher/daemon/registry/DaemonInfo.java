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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import org.gradle.internal.TimeProvider;
import org.gradle.internal.TrueTimeProvider;
import org.gradle.internal.remote.Address;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;

import java.io.Serializable;
import java.util.Date;

/**
 * Provides information about a daemon that is potentially available to do some work.
 */
public class DaemonInfo implements Serializable, DaemonConnectDetails {

    private final Address address;
    private final DaemonContext context;
    private final byte[] token;
    private final TimeProvider timeProvider;

    private boolean idle;
    private long lastBusy;

    public DaemonInfo(Address address, DaemonContext context, byte[] token, boolean idle) {
        this(address, context, token, idle, new TrueTimeProvider());
    }

    @VisibleForTesting
    DaemonInfo(Address address, DaemonContext context, byte[] token, boolean idle, TimeProvider busyClock) {
        this.address = Preconditions.checkNotNull(address);
        this.context = Preconditions.checkNotNull(context);
        this.token = Preconditions.checkNotNull(token);
        this.timeProvider = Preconditions.checkNotNull(busyClock);
        this.lastBusy = -1; // Will be overwritten by setIdle if not idle.
        setIdle(idle);
    }

    public DaemonInfo setIdle(boolean idle) {
        this.idle = idle;
        if (!idle) {
            lastBusy = timeProvider.getCurrentTime();
        }
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

    public byte[] getToken() {
        return token;
    }

    /** Last time the daemon was brought out of idle mode. */
    public Date getLastBusy() {
        return new Date(lastBusy);
    }

    @Override
    public String toString() {
        return String.format("DaemonInfo{pid=%s, address=%s, idle=%s, lastBusy=%s, context=%s}", context.getPid(), address, idle, lastBusy, context);
    }

}
