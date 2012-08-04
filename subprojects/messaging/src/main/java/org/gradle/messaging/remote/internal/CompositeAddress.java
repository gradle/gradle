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
package org.gradle.messaging.remote.internal;

import org.gradle.messaging.remote.Address;

public class CompositeAddress implements Address {
    private final Address address;
    private final Object qualifier;

    public CompositeAddress(Address address, Object qualifier) {
        this.address = address;
        this.qualifier = qualifier;
    }

    public String getDisplayName() {
        return String.format("%s:%s", address.getDisplayName(), qualifier);
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
        CompositeAddress other = (CompositeAddress) o;
        return other.address.equals(address) && other.qualifier.equals(qualifier);
    }

    @Override
    public int hashCode() {
        return address.hashCode() ^ qualifier.hashCode();
    }

    public Address getAddress() {
        return address;
    }

    public Object getQualifier() {
        return qualifier;
    }
}
