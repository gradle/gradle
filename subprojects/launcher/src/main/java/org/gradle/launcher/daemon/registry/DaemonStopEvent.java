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

import java.io.Serializable;
import java.util.Date;

/**
 * Information regarding when and why a daemon was stopped.
 */
public class DaemonStopEvent implements Serializable {
    private final Date timestamp;
    private final String reason;

    public DaemonStopEvent(Date timestamp, String reason) {
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getReason() {
        return reason;
    }
}
