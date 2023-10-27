/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.service.scopes;

import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.execution.UnitOfWork;
import org.gradle.internal.execution.WorkInputListener;
import org.gradle.internal.execution.WorkInputListeners;
import org.gradle.internal.properties.InputBehavior;

import java.util.EnumSet;

public class DefaultWorkInputListeners implements WorkInputListeners {
    private final AnonymousListenerBroadcast<WorkInputListener> broadcaster;

    public DefaultWorkInputListeners(ListenerManager listenerManager) {
        broadcaster = listenerManager.createAnonymousBroadcaster(WorkInputListener.class);
    }

    @Override
    public void addListener(WorkInputListener listener) {
        broadcaster.add(listener);
    }

    @Override
    public void removeListener(WorkInputListener listener) {
        broadcaster.remove(listener);
    }

    @Override
    public void broadcastFileSystemInputsOf(UnitOfWork.Identity identity, UnitOfWork work, EnumSet<InputBehavior> relevantBehaviors) {
        broadcaster.getSource().onExecute(identity, work, relevantBehaviors);
    }
}
