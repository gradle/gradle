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

package org.gradle.launcher.daemon.registry;

import org.gradle.api.Nullable;
import org.gradle.launcher.daemon.server.expiry.DaemonExpirationStatus;

import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

/**
 * Information regarding when and why a daemon was stopped.
 */
public class DaemonStopEvent implements Serializable {
    private final Date timestamp;
    private final long pid;
    @Nullable
    private final DaemonExpirationStatus status;
    @Nullable
    private final String reason;

    public DaemonStopEvent(Date timestamp, long pid, @Nullable DaemonExpirationStatus status, @Nullable String reason) {
        this.timestamp = timestamp;
        this.status = status;
        this.reason = reason;
        this.pid = pid;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public long getPid() {
        return pid;
    }

    @Nullable
    public DaemonExpirationStatus getStatus() {
        return status;
    }

    @Nullable
    public String getReason() {
        if (reason == null) {
            return "";
        }
        return reason;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DaemonStopEvent stopEvent = (DaemonStopEvent) o;
        return timestamp.equals(stopEvent.timestamp)
            && (reason != null ? reason.equals(stopEvent.reason) : stopEvent.reason == null);
    }

    @Override
    public int hashCode() {
        int result = timestamp.hashCode();
        result = 31 * result + (reason != null ? reason.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "DaemonStopEvent{"
            + "timestamp=" + timestamp
            + ", pid=" + pid
            + ", status=" + status
            + "}";
    }

    boolean occurredInLastHours(final int numHours) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date(System.currentTimeMillis()));
        cal.add(Calendar.HOUR_OF_DAY, -1 * numHours);
        return timestamp.after(cal.getTime());
    }
}
