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

package org.gradle.messaging.actor.internal;

import org.gradle.api.logging.Logging;
import org.gradle.messaging.actor.Actor;
import org.gradle.messaging.actor.ActorFactory;
import org.gradle.messaging.concurrent.CompositeStoppable;
import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.Stoppable;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.messaging.dispatch.*;

import java.util.IdentityHashMap;
import java.util.Map;

public class DefaultActorFactory implements ActorFactory, Stoppable {
    private final Map<Object, ActorImpl> actors = new IdentityHashMap<Object, ActorImpl>();
    private final Object lock = new Object();
    private final ExecutorFactory executorFactory;

    public DefaultActorFactory(ExecutorFactory executorFactory) {
        this.executorFactory = executorFactory;
    }

    public void stop() {
        synchronized (lock) {
            try {
                new CompositeStoppable(actors.values()).stop();
            } finally {
                actors.clear();
            }
        }
    }

    public Actor createActor(Object target) {
        if (target instanceof ActorImpl) {
            return (ActorImpl) target;
        }
        synchronized (lock) {
            ActorImpl actor = actors.get(target);
            if (actor == null) {
                actor = new ActorImpl(target);
                actors.put(target, actor);
            }
            return actor;
        }
    }

    private void stopped(ActorImpl actor) {
        synchronized (lock) {
            actors.values().remove(actor);
        }
    }

    private class ActorImpl implements Actor {
        private final StoppableDispatch<MethodInvocation> dispatch;
        private final StoppableExecutor executor;
        private final ExceptionTrackingListener exceptionListener;

        public ActorImpl(Object targetObject) {
            executor = executorFactory.create(String.format("Dispatch %s", targetObject));
            exceptionListener = new ExceptionTrackingListener(Logging.getLogger(ActorImpl.class));
            dispatch = new AsyncDispatch<MethodInvocation>(executor, new ExceptionTrackingDispatch<MethodInvocation>(
                    new ReflectionDispatch(targetObject), exceptionListener));
        }

        public <T> T getProxy(Class<T> type) {
            return new ProxyDispatchAdapter<T>(type, this).getSource();
        }

        public void stop() {
            try {
                new CompositeStoppable(dispatch, executor, exceptionListener).stop();
            } finally {
                stopped(this);
            }
        }

        public void dispatch(MethodInvocation message) {
            dispatch.dispatch(message);
        }
    }
}
