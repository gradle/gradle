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

package org.gradle.launcher.daemon.client;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import org.gradle.api.specs.Spec;
import org.gradle.internal.SystemProperties;
import org.gradle.launcher.daemon.registry.DaemonStopEvent;
import org.gradle.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaemonStartingMessage {
    public static final String STARTING_DAEMON_MESSAGE = "Starting a new Gradle Daemon for this build: subsequent builds will be faster";
    public static final String ONE_BUSY_DAEMON_MESSAGE = "a busy daemon can't run this build";
    public static final String MULTIPLE_BUSY_DAEMONS_MESSAGE = " busy daemons can't run this build";
    public static final String ONE_INCOMPATIBLE_DAEMON_MESSAGE = "an idle daemon with different JVM constraints can't run this build";
    public static final String MULTIPLE_INCOMPATIBLE_DAEMONS_MESSAGE = " idle daemons with different JVM constraints can't run this build";
    public static final String ONE_DAEMON_STOPPED_PREFIX = "a previous daemon was stopped ";
    public static final String MULTIPLE_DAEMONS_STOPPED_PREFIX = " previous daemons were stopped ";
    public static final String NO_COMPATIBLE_DAEMONS_MESSAGE = "No compatible daemons found:";
    private static final String LINE_SEPARATOR = SystemProperties.getInstance().getLineSeparator();

    public static String generate(final int numBusy, final int numIncompatible, final List<DaemonStopEvent> stopEvents) {
        String message = "";

        // User likely doesn't care about daemons that stopped a long time ago
        List<DaemonStopEvent> recentStopEvents = CollectionUtils.filter(stopEvents, new Spec<DaemonStopEvent>() {
            public boolean isSatisfiedBy(DaemonStopEvent event) {
                return event.occurredInLastDays(1);
            }
        });

        if (numBusy + numIncompatible + recentStopEvents.size() > 0) {
            final List<String> reasons = Lists.newArrayList(NO_COMPATIBLE_DAEMONS_MESSAGE);
            if (numBusy > 0) {
                reasons.add(numBusy > 1 ? numBusy + MULTIPLE_BUSY_DAEMONS_MESSAGE : ONE_BUSY_DAEMON_MESSAGE);
            }
            if (numIncompatible > 0) {
                reasons.add(numIncompatible > 1 ? numIncompatible + MULTIPLE_INCOMPATIBLE_DAEMONS_MESSAGE : ONE_INCOMPATIBLE_DAEMON_MESSAGE);
            }

            if (recentStopEvents.size() > 0) {
                for (Map.Entry<String, Integer> entry : countByReason(recentStopEvents).entrySet()) {
                    final Integer numStopped = entry.getValue();
                    final String prefix = numStopped > 1 ? numStopped + MULTIPLE_DAEMONS_STOPPED_PREFIX : ONE_DAEMON_STOPPED_PREFIX;
                    reasons.add(prefix + entry.getKey());
                }
            }

            message += Joiner.on(LINE_SEPARATOR + "  - ").skipNulls().join(reasons) + LINE_SEPARATOR;
        }

        return message + STARTING_DAEMON_MESSAGE;
    }

    private static Map<String, Integer> countByReason(List<DaemonStopEvent> stopEvents) {
        Map<String, Integer> countByReason = new HashMap<String, Integer>();
        for (DaemonStopEvent event : stopEvents) {
            final String reason = event.getReason();
            Integer count = countByReason.get(reason) == null ? 0 : countByReason.get(reason);
            countByReason.put(reason, count + 1);
        }
        return countByReason;
    }
}
