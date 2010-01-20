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

import org.gradle.api.GradleException;
import org.gradle.listener.dispatch.*;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Creates a proxy object which dispatches method calls to a target object asynchronously.
 *
 * @param <T>
 */
public class AsyncProxy<T> {
    private final AsyncDispatch<Event> asyncDispatch;
    private final ProxyDispatchAdapter<T> adapter;

    /**
     * Creates an unstarted proxy. Any calls to the source of this proxy will be queued until {@link #start(Object)} has
     * been called to start the proxy.
     */
    public AsyncProxy(Class<T> type) {
        this(type, null);
    }

    /**
     * Creates a started proxy which dispatches to the given target object.
     */
    public AsyncProxy(Class<T> type, T target) {
        this(type, target, Executors.newSingleThreadExecutor());
    }

    /**
     * Creates a started proxy which dispatches to the given target object.
     */
    public AsyncProxy(Class<T> type, T target, Executor executor) {
        asyncDispatch = new AsyncDispatch<Event>(executor);
        if (target != null) {
            asyncDispatch.start(new ReflectionDispatch(type, target));
        }
        adapter = new ProxyDispatchAdapter<T>(type, asyncDispatch);
    }

    public T getSource() {
        return adapter.getSource();
    }

    /**
     * Starts this proxy. All method calls are dispatched to the given target object.
     */
    public void start(T target) {
        asyncDispatch.start(new ReflectionDispatch(adapter.getType(), target));
    }

    /**
     * Stops this proxy. Blocks until all method calls have been dispatched to the target object.
     */
    public void stop() {
        asyncDispatch.stop();
    }

    private static class ReflectionDispatch implements Dispatch<Event> {
        private final Class<?> type;
        private final AtomicReference<Object> target;

        public ReflectionDispatch(Class<?> type, Object target) {
            this.type = type;
            this.target = new AtomicReference<Object>(target);
        }

        public void dispatch(Event message) {
            try {
                message.getMethod(type).invoke(target.get(), message.getArguments());
            } catch (InvocationTargetException e) {
                throw new GradleException(e.getCause());
            } catch (Exception e) {
                throw new GradleException(e);
            }
        }

        public void setTarget(Object target) {
            this.target.set(target);
        }
    }
}
