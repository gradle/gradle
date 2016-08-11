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
import org.gradle.launcher.daemon.server.api.DaemonStateControl.State;

import java.io.Serializable;
import java.util.Date;

import static org.gradle.launcher.daemon.server.api.DaemonStateControl.State.*;

/**
 * Provides information about a daemon that is potentially available to do some work.
 */
public class DaemonInfo implements Serializable, DaemonConnectDetails {

    private final Address address;
    private final DaemonContext context;
    private final byte[] token;
    private final TimeProvider timeProvider;

    private State state;
    private long lastBusy;

    public DaemonInfo(Address address, DaemonContext context, byte[] token, State state) {
        this(address, context, token, state, new TrueTimeProvider());
    }

    @VisibleForTesting
    DaemonInfo(Address address, DaemonContext context, byte[] token, State state, TimeProvider busyClock) {
        this.address = Preconditions.checkNotNull(address);
        this.context = Preconditions.checkNotNull(context);
        this.token = Preconditions.checkNotNull(token);
        this.timeProvider = Preconditions.checkNotNull(busyClock);
        this.lastBusy = -1; // Will be overwritten by setIdle if not idle.
        setState(state);
    }

    public DaemonInfo setState(State state) {
        if ((this.state == Idle || this.state == null) && state == Busy) {
            lastBusy = timeProvider.getCurrentTime();
        }
        this.state = state;
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

    public State getState() {
        return state;
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
        return String.format("DaemonInfo{pid=%s, address=%s, state=%s, lastBusy=%s, context=%s}", context.getPid(), address, state, lastBusy, context);
    }

}
