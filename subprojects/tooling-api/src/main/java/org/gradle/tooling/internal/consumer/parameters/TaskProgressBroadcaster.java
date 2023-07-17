/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.tooling.events.task.TaskProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;
import org.gradle.tooling.internal.protocol.events.InternalTaskDescriptor;

public class TaskProgressBroadcaster extends ProgressListenerBroadcasterAdapter implements ProgressListenerBroadcaster{
    public TaskProgressBroadcaster() {
        super(TaskProgressEvent.class, InternalTaskDescriptor.class, null);
    }

    @Override
    public void broadCast(Object progressEvent) {

    }

    @Override
    public void broadCastInterProgressEvent(InternalProgressEvent progressEvent) {

    }
}
