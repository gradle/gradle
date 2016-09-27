/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.internal.remote.internal.inet;

import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.List;

public class SocketInetAddress implements InetEndpoint {
    public static final org.gradle.internal.serialize.Serializer<SocketInetAddress> SERIALIZER = new Serializer();

    private final InetAddress address;
    private final int port;

    public SocketInetAddress(InetAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    public String getDisplayName() {
        return address + ":" + port;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        SocketInetAddress other = (SocketInetAddress) o;
        return other.address.equals(address) && other.port == port;
    }

    @Override
    public int hashCode() {
        return address.hashCode() ^ port;
    }

    public List<InetAddress> getCandidates() {
        return Collections.singletonList(address);
    }

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    private static class Serializer implements org.gradle.internal.serialize.Serializer<SocketInetAddress> {

        @Override
        public SocketInetAddress read(Decoder decoder) throws Exception {
            return new SocketInetAddress(readAddress(decoder), decoder.readInt());
        }

        private InetAddress readAddress(Decoder decoder) throws IOException {
            return InetAddress.getByAddress(decoder.readBinary());
        }

        @Override
        public void write(Encoder encoder, SocketInetAddress address) throws Exception {
            writeAddress(encoder, address);
            encoder.writeInt(address.port);
        }

        private void writeAddress(Encoder encoder, SocketInetAddress address) throws IOException {
            encoder.writeBinary(address.address.getAddress());
        }
    }
}
