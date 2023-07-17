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

import org.gradle.api.NonNullApi;
import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.events.ProgressListener;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import javax.annotation.Nullable;

@NonNullApi
public class ProgressListenerBroadcasterAdapter {
    private ListenerBroadcast<ProgressListener> listeners = new ListenerBroadcast<>(ProgressListener.class);
    private final Class<?> handledEventType;
    private final Class<?> handleProgressEventType;
    private final @Nullable Class<?> descriptorClass;

    public ProgressListenerBroadcasterAdapter(Class<?> handledEventType, Class<?> handleProgressEventType, @Nullable Class<?> descriptorClass) {
        this.handledEventType = handledEventType;
        this.handleProgressEventType = handleProgressEventType;
        this.descriptorClass = descriptorClass;
    }

    public boolean canHandle(Class<?> eventType) {
        return eventType.isAssignableFrom(handledEventType);
    }

    public boolean canHandleProgressEvent(Class<?> progressEventType) {
        return handleProgressEventType.isAssignableFrom(progressEventType);
    }

    public ListenerBroadcast<ProgressListener> getListeners() {
        return listeners;
    }

    public boolean canHandleDescriptor(Class<?> aClass) {
        return descriptorClass != null && descriptorClass.isAssignableFrom(aClass);
    }

    public void broadCastInterProgressEvent(InternalProgressEvent progressEvent) {
//        listeners.getSource().onProgress(progressEvent);
    }
}
