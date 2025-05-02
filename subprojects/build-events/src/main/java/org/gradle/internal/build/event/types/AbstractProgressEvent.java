/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.internal.build.event.types;

import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.tooling.internal.protocol.events.InternalProgressEvent;

import java.io.Serializable;

public abstract class AbstractProgressEvent<T extends InternalOperationDescriptor> implements Serializable, InternalProgressEvent {
    private final long eventTime;
    private final T descriptor;

    protected AbstractProgressEvent(long eventTime, T descriptor) {
        this.eventTime = eventTime;
        this.descriptor = descriptor;
    }

    @Override
    public long getEventTime() {
        return eventTime;
    }

    @Override
    public T getDescriptor() {
        return descriptor;
    }
}
