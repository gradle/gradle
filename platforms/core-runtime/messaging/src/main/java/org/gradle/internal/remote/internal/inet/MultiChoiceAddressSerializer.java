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
import org.gradle.internal.serialize.Serializer;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultiChoiceAddressSerializer implements Serializer<MultiChoiceAddress> {
    @Override
    public MultiChoiceAddress read(Decoder decoder) throws IOException {
        UUID canonicalAddress = new UUID(decoder.readLong(), decoder.readLong());
        int port = decoder.readInt();
        int addressCount = decoder.readSmallInt();
        List<InetAddress> addresses = new ArrayList<InetAddress>(addressCount);
        for (int i = 0; i < addressCount; i++) {
            InetAddress address = InetAddress.getByAddress(decoder.readBinary());
            addresses.add(address);
        }
        return new MultiChoiceAddress(canonicalAddress, port, addresses);
    }

    @Override
    public void write(Encoder encoder, MultiChoiceAddress address) throws IOException {
        UUID canonicalAddress = address.getCanonicalAddress();
        encoder.writeLong(canonicalAddress.getMostSignificantBits());
        encoder.writeLong(canonicalAddress.getLeastSignificantBits());
        encoder.writeInt(address.getPort());
        encoder.writeSmallInt(address.getCandidates().size());
        for (InetAddress inetAddress : address.getCandidates()) {
            encoder.writeBinary(inetAddress.getAddress());
        }
    }
}
