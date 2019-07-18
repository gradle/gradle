/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.actor.internal;

import org.gradle.internal.actor.Actor;
import org.gradle.internal.actor.ActorFactory;
import org.gradle.internal.concurrent.*;
import org.gradle.internal.dispatch.*;
import org.slf4j.LoggerFactory;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A basic {@link ActorFactory} implementation. Currently cannot support creating both a blocking and non-blocking actor for the same target object.
 */
public class DefaultActorFactory implements ActorFactory, Stoppable {
    private final Map<Object, NonBlockingActor> nonBlockingActors = new IdentityHashMap<Object, NonBlockingActor>();
    private final Map<Object, BlockingActor> blockingActors = new IdentityHashMap<Object, BlockingActor>();
    private final Object lock = new Object();
    private final ExecutorFactory executorFactory;

    public DefaultActorFactory(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    /**
     * Stops all actors.
     */
    @Override
    public void stop() {
        synchronized (lock) {
            try {
                CompositeStoppable.stoppable(nonBlockingActors.values()).add(blockingActors.values()).stop();
            } finally {
                nonBlockingActors.clear();
            }
        }
    }

    @Override
    public Actor createActor(Object target) {
        if (target instanceof NonBlockingActor) {
            return (NonBlockingActor) target;
        }
        synchronized (lock) {
            if (blockingActors.containsKey(target)) {
                throw new UnsupportedOperationException("Cannot create a non-blocking and blocking actor for the same object. This is not implemented yet.");
            }
            NonBlockingActor actor = nonBlockingActors.get(target);
            if (actor == null) {
                actor = new NonBlockingActor(target);
                nonBlockingActors.put(target, actor);
            }
            return actor;
        }
    }

    @Override
    public Actor createBlockingActor(Object target) {
        synchronized (lock) {
            if (nonBlockingActors.containsKey(target)) {
                throw new UnsupportedOperationException("Cannot create a non-blocking and blocking actor for the same object. This is not implemented yet.");
            }
            BlockingActor actor = blockingActors.get(target);
            if (actor == null) {
                actor = new BlockingActor(target);
                blockingActors.put(target, actor);
            }
            return actor;
        }
    }

    private void stopped(NonBlockingActor actor) {
        synchronized (lock) {
            nonBlockingActors.values().remove(actor);
        }
    }

    private void stopped(BlockingActor actor) {
        synchronized (lock) {
            blockingActors.values().remove(actor);
        }
    }

    private class BlockingActor implements Actor {
        private final Dispatch<MethodInvocation> dispatch;
        private final Object lock = new Object();
        private boolean stopped;

        public BlockingActor(Object target) {
            dispatch = new ReflectionDispatch(target);
        }

        @Override
        public <T> T getProxy(Class<T> type) {
            return new ProxyDispatchAdapter<T>(this, type, ThreadSafe.class).getSource();
        }

        @Override
        public void stop() throws DispatchException {
            synchronized (lock) {
                stopped = true;
            }
            stopped(this);
        }

        @Override
        public void dispatch(MethodInvocation message) {
            synchronized (lock) {
                if (stopped) {
                    throw new IllegalStateException("This actor has been stopped.");
                }
                dispatch.dispatch(message);
            }
        }
    }

    private class NonBlockingActor implements Actor {
        private final Dispatch<MethodInvocation> dispatch;
        private final ManagedExecutor executor;
        private final ExceptionTrackingFailureHandler failureHandler;

        public NonBlockingActor(Object targetObject) {
            executor = executorFactory.create("Dispatch " + targetObject);
            failureHandler = new ExceptionTrackingFailureHandler(LoggerFactory.getLogger(NonBlockingActor.class));
            dispatch = new AsyncDispatch<MethodInvocation>(executor,
                    new FailureHandlingDispatch<MethodInvocation>(
                            new ReflectionDispatch(targetObject),
                            failureHandler), Integer.MAX_VALUE);
        }

        @Override
        public <T> T getProxy(Class<T> type) {
            return new ProxyDispatchAdapter<T>(this, type, ThreadSafe.class).getSource();
        }

        @Override
        public void stop() {
            try {
                CompositeStoppable.stoppable(dispatch, executor, failureHandler).stop();
            } finally {
                stopped(this);
            }
        }

        @Override
        public void dispatch(MethodInvocation message) {
            dispatch.dispatch(message);
        }
    }
}
