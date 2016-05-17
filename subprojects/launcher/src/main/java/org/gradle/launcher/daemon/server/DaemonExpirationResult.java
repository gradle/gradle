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

package org.gradle.launcher.daemon.server;

import org.gradle.api.Nullable;

public class DaemonExpirationResult {
    public static final DaemonExpirationResult DO_NOT_EXPIRE = new DaemonExpirationResult(false, false, null);

    private final boolean expired;
    private final boolean immediate;
    private final String reason;

    public DaemonExpirationResult(boolean expired, boolean immediate, @Nullable String reason) {
        this.expired = expired;
        this.immediate = immediate;
        this.reason = reason;
    }

    public DaemonExpirationResult(boolean expired, @Nullable String reason) {
        this(expired, false, reason);
    }

    public boolean isExpired() {
        return expired;
    }

    public String getReason() {
        return reason;
    }

    public boolean isImmediate() {
        return immediate;
    }
}
