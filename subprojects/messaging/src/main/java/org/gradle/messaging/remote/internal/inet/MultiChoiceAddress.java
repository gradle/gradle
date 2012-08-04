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
package org.gradle.messaging.remote.internal.inet;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class MultiChoiceAddress implements InetEndpoint {
    private final Object canonicalAddress;
    private final int port;
    private final List<InetAddress> candidates;

    public MultiChoiceAddress(Object canonicalAddress, int port, List<InetAddress> candidates) {
        this.canonicalAddress = canonicalAddress;
        this.port = port;
        this.candidates = new ArrayList<InetAddress>(candidates);
    }

    public String getDisplayName() {
        return String.format("[%s port:%s, addresses:%s]", canonicalAddress, port, candidates);
    }

    public Object getCanonicalAddress() {
        return canonicalAddress;
    }

    public List<InetAddress> getCandidates() {
        return candidates;
    }

    public int getPort() {
        return port;
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
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MultiChoiceAddress other = (MultiChoiceAddress) o;
        return other.canonicalAddress.equals(canonicalAddress);
    }

    @Override
    public int hashCode() {
        return canonicalAddress.hashCode();
    }

    public MultiChoiceAddress addAddresses(Iterable<InetAddress> candidates) {
        return new MultiChoiceAddress(canonicalAddress, port, Lists.newArrayList(Iterables.concat(candidates, this.candidates)));
    }
}
