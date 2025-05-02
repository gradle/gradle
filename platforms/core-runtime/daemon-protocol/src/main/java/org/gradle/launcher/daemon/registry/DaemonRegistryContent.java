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

import org.gradle.internal.remote.Address;
import org.gradle.internal.remote.internal.inet.InetEndpoint;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddress;
import org.gradle.internal.remote.internal.inet.MultiChoiceAddressSerializer;
import org.gradle.internal.remote.internal.inet.SocketInetAddress;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class DaemonRegistryContent implements Serializable {

    public static final org.gradle.internal.serialize.Serializer<DaemonRegistryContent> SERIALIZER = new Serializer();

    private static final MultiChoiceAddressSerializer MULTI_CHOICE_ADDRESS_SERIALIZER = new MultiChoiceAddressSerializer();

    private final Map<Address, DaemonInfo> infosMap;
    private final List<DaemonStopEvent> stopEvents;

    public DaemonRegistryContent() {
        infosMap = new LinkedHashMap<Address, DaemonInfo>();
        stopEvents = new ArrayList<DaemonStopEvent>();
    }

    private DaemonRegistryContent(Map<Address, DaemonInfo> infosMap, List<DaemonStopEvent> stopEvents) {
        this.infosMap = infosMap;
        this.stopEvents = stopEvents;
    }

    /**
     * returns all statuses. May be empty.
     */
    public List<DaemonInfo> getInfos() {
        return new LinkedList<DaemonInfo>(infosMap.values());
    }

    /**
     * Gets the status for given address. May return null.
     */
    public DaemonInfo getInfo(Address address) {
        return infosMap.get(address);
    }

    /**
     * Removes the status
     */
    public void removeInfo(int port) {
        infosMap.keySet().removeIf(address -> ((InetEndpoint) address).getPort() == port);
    }

    /**
     * Returns all stop events. May be empty.
     */
    public List<DaemonStopEvent> getStopEvents() {
        return stopEvents;
    }

    /**
     * Add stop event to our collection
     */
    public void addStopEvent(DaemonStopEvent stopEvent) {
        stopEvents.add(stopEvent);
    }

    /**
     * Removes all stop events.
     */
    public void removeStopEvents(Collection<DaemonStopEvent> events) {
        stopEvents.removeAll(events);
    }

    /**
     * sets the daemonInfo for given address
     */
    public void setStatus(Address address, DaemonInfo daemonInfo) {
        infosMap.put(address, daemonInfo);
    }

    /**
     * Data layout:
     * 0 - number of daemon infos
     * 1 - addresses
     * 2 - daemon infos
     * 3 - number of stop events
     * 4 - stop events
     */
    private static class Serializer implements org.gradle.internal.serialize.Serializer<DaemonRegistryContent> {

        @Override
        public DaemonRegistryContent read(Decoder decoder) throws Exception {
            if (decoder.readBoolean()) {
                List<Address> addresses = readAddresses(decoder);
                Map<Address, DaemonInfo> infosMap = readInfosMap(decoder, addresses);
                List<DaemonStopEvent> stopEvents = readStopEvents(decoder);
                return new DaemonRegistryContent(infosMap, stopEvents);
            }
            return null;
        }

        private List<DaemonStopEvent> readStopEvents(Decoder decoder) throws Exception {
            int len = decoder.readInt();
            List<DaemonStopEvent> out = new ArrayList<DaemonStopEvent>(len);
            for (int i = 0; i < len; i++) {
                out.add(DaemonStopEvent.SERIALIZER.read(decoder));
            }
            return out;
        }

        private Map<Address, DaemonInfo> readInfosMap(Decoder decoder, List<Address> addresses) throws Exception {
            Map<Address, DaemonInfo> infosMap = new LinkedHashMap<Address, DaemonInfo>(addresses.size());
            DaemonInfo.Serializer daemonInfoSerializer = new DaemonInfo.Serializer(addresses);
            for (Address address : addresses) {
                infosMap.put(address, daemonInfoSerializer.read(decoder));
            }
            return infosMap;
        }

        @Override
        public void write(Encoder encoder, DaemonRegistryContent registry) throws Exception {
            if (registry != null) {
                encoder.writeBoolean(true);
                Map<Address, DaemonInfo> infosMap = registry.infosMap;
                int infosSize = infosMap.size();
                // make sure we can store it in order or we'll have surprises on deserialization
                List<Address> addresses = new ArrayList<Address>(infosMap.keySet());
                writeAddresses(encoder, infosSize, addresses);
                writeDaemonInfos(encoder, infosMap, addresses);
                writeStopEvents(encoder, registry);
            } else {
                encoder.writeBoolean(false);
            }
        }

        private void writeStopEvents(Encoder encoder, DaemonRegistryContent registry) throws Exception {
            List<DaemonStopEvent> stopEvents = registry.stopEvents;
            encoder.writeInt(stopEvents.size());
            for (DaemonStopEvent stopEvent : stopEvents) {
                DaemonStopEvent.SERIALIZER.write(encoder, stopEvent);
            }
        }

        private void writeDaemonInfos(Encoder encoder, Map<Address, DaemonInfo> infosMap, List<Address> addresses) throws Exception {
            DaemonInfo.Serializer daemonInfoSerializer = new DaemonInfo.Serializer(addresses);
            for (Address address : addresses) {
                DaemonInfo info = infosMap.get(address);
                daemonInfoSerializer.write(encoder, info);
            }
        }


        private void writeAddresses(Encoder encoder, int infosSize, List<Address> addresses) throws Exception {
            encoder.writeInt(infosSize);
            for (Address address : addresses) {
                byte type = (byte) (address instanceof SocketInetAddress ? 0
                    : address instanceof MultiChoiceAddress ? 1
                    : 2);
                encoder.writeByte(type);
                switch (type) {
                    case 0:
                        SocketInetAddress.SERIALIZER.write(encoder, (SocketInetAddress) address);
                        break;
                    case 1:
                        MULTI_CHOICE_ADDRESS_SERIALIZER.write(encoder, (MultiChoiceAddress) address);
                        break;
                    default:
                        ObjectOutputStream oos = new ObjectOutputStream(encoder.getOutputStream());
                        oos.writeObject(address);
                }
            }
        }

        private List<Address> readAddresses(Decoder decoder) throws Exception {
            int infosSize = decoder.readInt();
            List<Address> out = new ArrayList<Address>();
            for (int i = 0; i < infosSize; i++) {
                byte type = decoder.readByte();
                switch (type) {
                    case 0:
                        out.add(SocketInetAddress.SERIALIZER.read(decoder));
                        break;
                    case 1:
                        out.add(MULTI_CHOICE_ADDRESS_SERIALIZER.read(decoder));
                        break;
                    default:
                        ObjectInputStream ois = new ObjectInputStream(decoder.getInputStream());
                        out.add((Address) ois.readObject());
                }
            }
            return out;
        }
    }
}
