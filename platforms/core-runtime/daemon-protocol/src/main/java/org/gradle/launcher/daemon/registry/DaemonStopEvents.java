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

import org.gradle.api.specs.Spec;
import org.gradle.util.internal.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DaemonStopEvents {
    private static final int RECENTLY = 1;

    public static List<DaemonStopEvent> uniqueRecentDaemonStopEvents(final List<DaemonStopEvent> stopEvents) {
        final Set<Long> uniqueStoppedPids = new HashSet<Long>(stopEvents.size());
        final List<DaemonStopEvent> recentStopEvents = new ArrayList<DaemonStopEvent>(stopEvents.size());

        final List<DaemonStopEvent> sortedEvents = CollectionUtils.sort(stopEvents, new Comparator<DaemonStopEvent>() {
            @Override
            public int compare(DaemonStopEvent event1, DaemonStopEvent event2) {
                if (event1.getStatus() != null && event2.getStatus() == null) {
                    return -1;
                } else if (event1.getStatus() == null && event2.getStatus() != null) {
                    return 1;
                } else if (event1.getStatus() != null && event2.getStatus() != null) {
                    return event2.getStatus().compareTo(event1.getStatus());
                }
                return 0;
            }
        });

        // User likely doesn't care about daemons that stopped a long time ago
        for (DaemonStopEvent event : sortedEvents) {
            Long pid = event.getPid();
            if (event.occurredInLastHours(RECENTLY) && !uniqueStoppedPids.contains(pid)) {
                // We can only determine if two DaemonStopEvent point at the same daemon if we know the PIDs
                if (pid != null) {
                    uniqueStoppedPids.add(pid);
                }
                recentStopEvents.add(event);
            }
        }
        return recentStopEvents;
    }

    public static List<DaemonStopEvent> oldStopEvents(final List<DaemonStopEvent> stopEvents) {
        return CollectionUtils.filter(stopEvents, new Spec<DaemonStopEvent>() {
            @Override
            public boolean isSatisfiedBy(DaemonStopEvent event) {
                return !event.occurredInLastHours(RECENTLY);
            }
        });
    }
}
