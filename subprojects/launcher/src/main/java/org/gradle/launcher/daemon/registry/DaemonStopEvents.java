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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DaemonStopEvents {
    public static List<DaemonStopEvent> uniqueRecentDaemonStopEvents(final List<DaemonStopEvent> stopEvents) {
        final Set<Long> uniqueStoppedPids = new HashSet<Long>(stopEvents.size());
        final List<DaemonStopEvent> recentStopEvents = new ArrayList<DaemonStopEvent>(stopEvents.size());

        // Do not sort original list
        final List<DaemonStopEvent> stopEventsCopy = new ArrayList<DaemonStopEvent>(stopEvents);
        Collections.sort(stopEventsCopy);

        // User likely doesn't care about daemons that stopped a long time ago
        for (DaemonStopEvent event : stopEventsCopy) {
            if (event.occurredInLastHours(1) && !uniqueStoppedPids.contains(event.getPid())) {
                uniqueStoppedPids.add(event.getPid());
                recentStopEvents.add(event);
            }
        }
        return recentStopEvents;
    }

    public static List<DaemonStopEvent> oldStopEvents(final List<DaemonStopEvent> stopEvents) {
        final List<DaemonStopEvent> oldEvents = new ArrayList<DaemonStopEvent>(stopEvents.size());
        for (DaemonStopEvent event : stopEvents) {
            if (!event.occurredInLastHours(1)) {
                oldEvents.add(event);
            }
        }
        return oldEvents;
    }
}
