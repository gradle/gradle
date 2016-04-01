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

package org.gradle.internal.remote.internal.hub.queue;

import org.gradle.internal.UncheckedException;
import org.gradle.internal.dispatch.Dispatch;
import org.gradle.internal.remote.internal.hub.protocol.InterHubMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.locks.Condition;

public class EndPointQueue implements Dispatch<InterHubMessage> {
    private final List<InterHubMessage> queue = new ArrayList<InterHubMessage>();
    private final MultiEndPointQueue owner;
    private final Condition condition;

    public EndPointQueue(MultiEndPointQueue owner, Condition condition) {
        this.owner = owner;
        this.condition = condition;
    }

    public void dispatch(InterHubMessage message) {
        queue.add(message);
        condition.signalAll();
    }

    public void take(Collection<InterHubMessage> drainTo) {
        if (queue.isEmpty()) {
            owner.empty(this);
            while (queue.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }
        drainTo.addAll(queue);
        queue.clear();
    }

    public void stop() {
        owner.stopped(this);
    }
}
