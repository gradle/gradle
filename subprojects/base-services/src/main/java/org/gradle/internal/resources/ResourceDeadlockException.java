/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resources;

import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.internal.Pair;
import org.gradle.util.CollectionUtils;

import java.util.Iterator;
import java.util.List;

public class ResourceDeadlockException extends GradleException {
    public ResourceDeadlockException(Iterable<Pair<Thread, Iterable<ResourceLock>>> failedLocks) {
        super(getMessage(failedLocks));
    }

    private static String getMessage(Iterable<Pair<Thread, Iterable<ResourceLock>>> failedLocks) {
        StringBuilder s = new StringBuilder("A dead lock between resource locks has been detected:\n");
        for (Pair<Thread, Iterable<ResourceLock>> failedLock : failedLocks) {
            s.append(String.format("  %s is trying to get a lock on %s which is held by %s\n", failedLock.getLeft().getName(), getDisplayName(failedLock.getRight()), getOwnerName(failedLock.getRight())));
        }
        return s.toString();
    }

    private static String getDisplayName(Iterable<ResourceLock> failedLocks) {
        Iterator<ResourceLock> itr = failedLocks.iterator();
        if (itr.hasNext()) {
            ResourceLock resourceLock = itr.next();
            if (resourceLock instanceof LeaseHolder) {
                return ((LeaseHolder) resourceLock).getRoot().getDisplayName();
            } else {
                return resourceLock.getDisplayName();
            }
        } else {
            return "UNKNOWN";
        }
    }

    private static String getOwnerName(Iterable<ResourceLock> resourceLocks) {
        List<String> lockOwners = CollectionUtils.collect(resourceLocks, new Transformer<String, ResourceLock>() {
            @Override
            public String transform(ResourceLock resourceLock) {
                return resourceLock.getOwner() != null ? resourceLock.getOwner().getName() : "UNKNOWN";
            }
        });
        return CollectionUtils.join(", ", lockOwners);
    }
}
