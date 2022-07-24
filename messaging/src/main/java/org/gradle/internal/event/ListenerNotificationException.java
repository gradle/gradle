/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.internal.event;

import org.gradle.internal.dispatch.MethodInvocation;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;

import javax.annotation.Nullable;

/**
 * A {@code ListenerNotificationException} is thrown when a listener cannot be notified of an event.
 */
@Contextual
public class ListenerNotificationException extends DefaultMultiCauseException {
    private final MethodInvocation event;

    public ListenerNotificationException(@Nullable MethodInvocation event, String message, Iterable<? extends Throwable> causes) {
        super(message, causes);
        this.event = event;
    }

    public MethodInvocation getEvent() {
        return event;
    }
}
