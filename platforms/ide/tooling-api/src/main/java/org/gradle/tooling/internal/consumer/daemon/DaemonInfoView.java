/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.tooling.internal.consumer.daemon;

/**
 * One entry from a Gradle daemon registry file.
 */
final class DaemonInfoView {
    final AddressView address;
    final byte[] token;
    final DaemonStateView state;
    final long lastBusyMillis;
    final DaemonContextView context;

    DaemonInfoView(
        AddressView address,
        byte[] token,
        DaemonStateView state,
        long lastBusyMillis,
        DaemonContextView context
    ) {
        this.address = address;
        this.token = token;
        this.state = state;
        this.lastBusyMillis = lastBusyMillis;
        this.context = context;
    }
}
