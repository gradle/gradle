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

package org.gradle.launcher.daemon.server.expiry;

import javax.annotation.Nullable;

public class DaemonExpirationResult {
    public static final DaemonExpirationResult NOT_TRIGGERED = new DaemonExpirationResult(DaemonExpirationStatus.DO_NOT_EXPIRE, null);

    private final DaemonExpirationStatus status;
    private final String reason;

    public DaemonExpirationResult(DaemonExpirationStatus status, @Nullable String reason) {
        this.status = status;
        this.reason = reason;
    }

    public DaemonExpirationStatus getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }
}
