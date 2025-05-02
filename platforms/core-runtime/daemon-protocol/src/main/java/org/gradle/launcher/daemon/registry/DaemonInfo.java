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
import org.gradle.internal.remote.Address;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.time.Clock;
import org.gradle.internal.time.Time;
import org.gradle.launcher.daemon.context.DaemonConnectDetails;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DefaultDaemonContext;
import org.gradle.launcher.daemon.server.api.DaemonState;

import java.io.IOException;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

import static org.gradle.launcher.daemon.server.api.DaemonState.Busy;
import static org.gradle.launcher.daemon.server.api.DaemonState.Idle;

/**
 * Provides information about a daemon that is potentially available to do some work.
 */
public class DaemonInfo implements Serializable, DaemonConnectDetails {

    private final Address address;
    private final DaemonContext context;
    private final byte[] token;
    private final Clock clock;

    private DaemonState state;
    private long lastBusy;

    public DaemonInfo(Address address, DaemonContext context, byte[] token, DaemonState state) {
        this(address, context, token, state, Time.clock());
    }

    @VisibleForTesting
    DaemonInfo(Address address, DaemonContext context, byte[] token, DaemonState state, Clock busyClock) {
        this.address = Preconditions.checkNotNull(address);
        this.context = Preconditions.checkNotNull(context);
        this.token = Preconditions.checkNotNull(token);
        this.clock = Preconditions.checkNotNull(busyClock);
        this.lastBusy = -1; // Will be overwritten by setIdle if not idle.
        setState(state);
    }

    private DaemonInfo(Address address, DaemonContext context, byte[] token, DaemonState state, long lastBusy) {
        this.address = address;
        this.context = context;
        this.token = token;
        this.state = state;
        this.lastBusy = lastBusy;
        this.clock = Time.clock();
    }

    public DaemonInfo setState(DaemonState state) {
        if ((this.state == Idle || this.state == null) && state == Busy) {
            lastBusy = clock.getCurrentTime();
        }
        this.state = state;
        return this;
    }

    @Override
    public String getUid() {
        return context.getUid();
    }

    @Override
    public Long getPid() {
        return context.getPid();
    }

    @Override
    public Address getAddress() {
        return address;
    }

    public DaemonContext getContext() {
        return context;
    }

    public DaemonState getState() {
        return state;
    }

    @Override
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

    public static class Serializer implements org.gradle.internal.serialize.Serializer<DaemonInfo> {

        private final List<Address> addresses;

        public Serializer(List<Address> addresses) {
            this.addresses = addresses;
        }

        @Override
        public DaemonInfo read(Decoder decoder) throws Exception {
            Address address = addresses.get(decoder.readInt());
            byte[] token = decoder.readBinary();
            DaemonState state = DaemonState.values()[decoder.readByte()];
            long lastBusy = decoder.readLong();
            DaemonContext context = DefaultDaemonContext.SERIALIZER.read(decoder);
            return new DaemonInfo(address, context, token, state, lastBusy);
        }

        @Override
        public void write(Encoder encoder, DaemonInfo info) throws Exception {
            writeAddressIndex(encoder, info);
            encoder.writeBinary(info.token);
            encoder.writeByte((byte) info.state.ordinal());
            encoder.writeLong(info.lastBusy);
            DefaultDaemonContext.SERIALIZER.write(encoder, (DefaultDaemonContext) info.context);
        }

        private void writeAddressIndex(Encoder encoder, DaemonInfo info) throws IOException {
            int i = 0;
            for (Address address : addresses) {
                if (info.address.equals(address)) {
                    encoder.writeInt(i);
                    break;
                }
                i++;
            }
        }
    }

}
