/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.listener.ListenerBroadcast;
import org.gradle.tooling.ProgressEvent;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.internal.protocol.ProgressListenerVersion1;

import java.util.LinkedList;

class ProgressListenerAdapter implements ProgressListenerVersion1 {
    private final ListenerBroadcast<ProgressListener> listeners = new ListenerBroadcast<ProgressListener>(ProgressListener.class);
    private final LinkedList<String> stack = new LinkedList<String>();

    public void onOperationStart(final String description) {
        stack.addFirst(description == null ? "" : description);
        fireChangeEvent();
    }

    public void onOperationEnd() {
        stack.removeFirst();
        fireChangeEvent();
    }

    private void fireChangeEvent() {
        final String description = stack.isEmpty() ? "" : stack.getFirst();
        listeners.getSource().statusChanged(new ProgressEvent() {
            public String getDescription() {
                return description;
            }
        });
    }

    public void add(ProgressListener listener) {
        listeners.add(listener);
    }
}
