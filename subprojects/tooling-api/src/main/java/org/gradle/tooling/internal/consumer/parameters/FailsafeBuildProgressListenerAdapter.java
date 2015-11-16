/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.internal.event.ListenerNotificationException;
import org.gradle.tooling.internal.protocol.InternalBuildProgressListener;

import java.util.Collections;
import java.util.List;

public class FailsafeBuildProgressListenerAdapter implements InternalBuildProgressListener {
    private final InternalBuildProgressListener delegate;
    private Throwable listenerFailure;

    public FailsafeBuildProgressListenerAdapter(InternalBuildProgressListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onEvent(Object event) {
        if (listenerFailure != null) {
            // Discard event
            return;
        }
        try {
            delegate.onEvent(event);
        } catch (Throwable t) {
            listenerFailure = t;
        }
    }

    @Override
    public List<String> getSubscribedOperations() {
        return delegate.getSubscribedOperations();
    }

    public void rethrowErrors() {
        if (listenerFailure != null) {
            throw new ListenerNotificationException("One or more progress listeners failed with an exception.", Collections.singletonList(listenerFailure));
        }
    }
}
