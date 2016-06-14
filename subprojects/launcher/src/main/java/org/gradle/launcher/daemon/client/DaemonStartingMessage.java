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
import org.gradle.launcher.daemon.registry.DaemonStopEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DaemonStartingMessage {
    public static final String STARTING_DAEMON_MESSAGE = "Starting a Gradle Daemon";
    public static final String SUBSEQUENT_BUILDS_WILL_BE_FASTER = "(subsequent builds will be faster)";
    public static final String NOT_REUSED_MESSAGE = " could not be reused";

    public static String generate(final int numBusy, final int numIncompatible, final int numStopped) {
        final int totalUnavailableDaemons = numBusy + numIncompatible + numStopped;
        if (totalUnavailableDaemons > 0) {
            final List<String> reasons = Lists.newArrayList();
            if (numBusy > 0) {
                reasons.add(numBusy + " busy");
            }
            if (numIncompatible > 0) {
                reasons.add(numIncompatible + " incompatible");
            }
            if (numStopped > 0) {
                reasons.add(numStopped + " stopped");
            }

            return STARTING_DAEMON_MESSAGE + ", "
                + Joiner.on(" and ").join(reasons) + " Daemon" + (totalUnavailableDaemons > 1 ? "s" : "")
                + NOT_REUSED_MESSAGE;
        } else {
            return STARTING_DAEMON_MESSAGE + " " + SUBSEQUENT_BUILDS_WILL_BE_FASTER;
        }
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
