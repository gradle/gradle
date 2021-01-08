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

package org.gradle.api.execution.internal;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.event.AnonymousListenerBroadcast;
import org.gradle.internal.event.ListenerManager;

public class DefaultTaskInputsListeners implements TaskInputsListeners {

    private final AnonymousListenerBroadcast<TaskInputsListener> broadcaster;

    public DefaultTaskInputsListeners(ListenerManager listenerManager) {
        broadcaster = listenerManager.createAnonymousBroadcaster(TaskInputsListener.class);
    }

    @Override
    public void addListener(TaskInputsListener listener) {
        broadcaster.add(listener);
    }

    @Override
    public void removeListener(TaskInputsListener listener) {
        broadcaster.remove(listener);
    }

    @Override
    public void broadcastFileSystemInputsOf(TaskInternal task, FileCollectionInternal fileSystemInputs) {
        broadcaster.getSource().onExecute(task, fileSystemInputs);
    }
}
