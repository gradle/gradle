/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.logging.dispatch;

import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.internal.logging.events.AddListenerEvent;
import org.gradle.internal.logging.events.EndOutputEvent;
import org.gradle.internal.logging.events.FlushOutputEvent;
import org.gradle.internal.logging.events.OutputEvent;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.logging.events.RemoveListenerEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AsynchronousLogDispatcher extends Thread {
    private final Queue<OutputEvent> eventQueue = new ConcurrentLinkedQueue<OutputEvent>();
    private final ListenerBroadcast<OutputEventListener> listeners = new ListenerBroadcast<OutputEventListener>(OutputEventListener.class);
    private final OutputEventListener eventListener;
    private Throwable failure;

    public AsynchronousLogDispatcher() {
        super("Log dispatcher");
        setDaemon(true);
        this.eventListener = listeners.getSource();
    }

    public void submit(OutputEvent event) {
        eventQueue.add(event);
    }

    @Override
    public void run() {
        while (true) {
            try {
                OutputEvent event = eventQueue.poll();
                if (event == null) {
                    Thread.sleep(10);
                } else {
                    dispatchEvent(event);
                }
            } catch (Throwable t) {
                if (failure == null) {
                    failure = t;
                }
            }
        }
    }

    private void dispatchEvent(OutputEvent event) {
        if (event instanceof AddListenerEvent) {
            AddListenerEvent addListenerEvent = (AddListenerEvent) event;
            listeners.add(addListenerEvent.getListener());
            return;
        }
        if (event instanceof RemoveListenerEvent) {
            RemoveListenerEvent removeListenerEvent = (RemoveListenerEvent) event;
            listeners.remove(removeListenerEvent.getListener());
            try {
                removeListenerEvent.getListener().onOutput(new EndOutputEvent());
            } finally {
                removeListenerEvent.handled();
            }
            return;
        }

        try {
            eventListener.onOutput(event);
        } catch (Throwable t) {
            if (failure == null) {
                failure = t;
            }
        }
        if (event instanceof FlushOutputEvent) {
            FlushOutputEvent flushOutputEvent = (FlushOutputEvent) event;
            flushOutputEvent.handled(failure);
            failure = null;
        }
    }
}
