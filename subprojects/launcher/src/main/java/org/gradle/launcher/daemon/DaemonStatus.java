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

package org.gradle.launcher.daemon;

import org.gradle.messaging.remote.Address;

import java.io.Serializable;

/**
 * @author: Szczepan Faber, created at: 8/19/11
 */
public class DaemonStatus implements Serializable {

    private final Address address;
    private boolean idle = true;

    public DaemonStatus(Address address) {
        this.address = address;
    }

    public DaemonStatus setIdle(boolean idle) {
        this.idle = idle;
        return this;
    }

    public Address getAddress() {
        return address;
    }

    public boolean isIdle() {
        return idle;
    }

    @Override
    public String toString() {
        return "DaemonStatus{"
                + "address=" + address
                + ", idle=" + idle
                + '}';
    }
}
