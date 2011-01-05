/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.listener;

import org.gradle.api.Transformer;
import org.gradle.messaging.dispatch.AsyncDispatch;
import org.gradle.messaging.dispatch.MethodInvocation;
import org.gradle.messaging.dispatch.StoppableDispatch;

import java.util.concurrent.Executor;

/**
 * An {@code AsyncListenerBroadcast} is a {@code ListenerBroadcast} which dispatches events to listeners asynchronously
 * to the generation of the events. Events are delivered in the order generated, and ordering between listeners is
 * maintained.
 */
public class AsyncListenerBroadcast<T> extends ListenerBroadcast<T> {
    public AsyncListenerBroadcast(Class<T> type, final Executor executor) {
        super(type, new Transformer<StoppableDispatch<MethodInvocation>>() {
            public StoppableDispatch<MethodInvocation> transform(StoppableDispatch<MethodInvocation> original) {
                return new AsyncDispatch<MethodInvocation>(executor, original);
            }
        });
    }
}
