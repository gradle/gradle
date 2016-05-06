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

package org.gradle.launcher.daemon.server.health.gc;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.openmbean.CompositeData;
import java.lang.management.MemoryNotificationInfo;
import java.util.List;

public class GarbageCollectionEventFilter extends NotificationFilterSupport {
    final Logger logger = Logging.getLogger(GarbageCollectionEventFilter.class);
    final List<String> pools;

    public GarbageCollectionEventFilter(List<String> pools) {
        this.pools = pools;
        this.enableType(MemoryNotificationInfo.MEMORY_COLLECTION_THRESHOLD_EXCEEDED);
    }

    @Override
    public synchronized boolean isNotificationEnabled(Notification notification) {
        return super.isNotificationEnabled(notification) && matches(notification);
    }

    private boolean matches(Notification notification) {
        MemoryNotificationInfo info = MemoryNotificationInfo.from((CompositeData) notification.getUserData());
        logger.warn("Received memory notification for " + info.getPoolName());
        return pools.contains(info.getPoolName());
    }
}
