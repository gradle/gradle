/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification;
import org.gradle.internal.operations.notify.BuildOperationNotificationListener;
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar;
import org.gradle.internal.operations.notify.BuildOperationStartedNotification;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BuildOperationNotificationBridge implements Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationNotificationBridge.class);

    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();

    private final BuildOperationListenerManager buildOperationListenerManager;

    public BuildOperationNotificationBridge(BuildOperationListenerManager buildOperationListenerManager) {
        this.buildOperationListenerManager = buildOperationListenerManager;
    }

    public BuildOperationNotificationListenerRegistrar notificationListenerRegistrar() {
        return new BuildOperationNotificationListenerRegistrar() {
            @Override
            public void registerBuildScopeListener(BuildOperationNotificationListener notificationListener) {
                Listener listener = new Listener(notificationListener, buildOperationListenerManager);
                listeners.add(listener);
                buildOperationListenerManager.addListener(listener);
            }
        };
    }

    @Override
    public void stop() {
        CompositeStoppable.stoppable(listeners).stop();
    }

    /*
        Note: the intention here is to work towards not having to create new objects
        to meet the notification object interfaces.
        Instead, the base types like BuildOperationDescriptor should implement them natively.
        However, this will require restructuring this type and associated things such as
        OperationStartEvent. This will happen later.
     */

    private static class Listener implements BuildOperationListener, Stoppable {

        private final BuildOperationNotificationListener listener;
        private final BuildOperationListenerManager buildOperationListenerManager;

        private final Map<Object, Object> active = new ConcurrentHashMap<Object, Object>();

        private Listener(BuildOperationNotificationListener listener, BuildOperationListenerManager buildOperationListenerManager) {
            this.listener = listener;
            this.buildOperationListenerManager = buildOperationListenerManager;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            // replace this with opt-in to exposing on producer side
            // it just so happens right now that this is a reasonable heuristic
            if (buildOperation.getDetails() == null) {
                return;
            }

            active.put(buildOperation.getId(), 1);

            Started notification = new Started(buildOperation.getId(), buildOperation.getDetails());
            try {
                listener.started(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            if (active.remove(buildOperation.getId()) == null) {
                return;
            }

            Finished notification = new Finished(buildOperation.getId(), buildOperation.getDetails(), finishEvent.getResult(), finishEvent.getFailure());
            try {
                listener.finished(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }

        @Override
        public void stop() {
            buildOperationListenerManager.removeListener(this);
            active.clear();
        }
    }

    private static class Started implements BuildOperationStartedNotification {

        private final Object id;
        private final Object details;

        private Started(Object id, Object details) {
            this.id = id;
            this.details = details;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{"
                + "id=" + id
                + ", details=" + details
                + '}';
        }
    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final Object id;
        private final Object details;
        private final Object result;
        private final Throwable failure;

        private Finished(Object id, Object details, Object result, Throwable failure) {
            this.id = id;
            this.details = details;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public Object getNotificationOperationResult() {
            return result;
        }

        @Override
        public Throwable getNotificationOperationFailure() {
            return failure;
        }

        @Override
        public String toString() {
            return "BuildOperationFinishedNotification{"
                + "id=" + id
                + ", details=" + details
                + ", result=" + result
                + ", failure=" + failure
                + '}';
        }
    }

}
