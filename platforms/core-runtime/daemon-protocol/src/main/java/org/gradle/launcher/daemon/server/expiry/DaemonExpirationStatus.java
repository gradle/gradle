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

/**
 * Expiration status for daemon expiration check results.  Note that order here is important, higher ordinal statuses take precedent over lower ordinal statuses when aggregating results in {@link
 * AllDaemonExpirationStrategy}.
 */
public enum DaemonExpirationStatus {
    DO_NOT_EXPIRE,
    QUIET_EXPIRE,
    GRACEFUL_EXPIRE,
    IMMEDIATE_EXPIRE;

    public static DaemonExpirationStatus highestPriorityOf(DaemonExpirationStatus left, DaemonExpirationStatus right) {
        if (left.ordinal() > right.ordinal()) {
            return left;
        } else {
            return right;
        }
    }
}
