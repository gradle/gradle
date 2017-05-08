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

package org.gradle.internal.operations.notify;

import org.gradle.api.Action;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBuildOperationNotificationListenerRegistrar implements BuildOperationNotificationListenerRegistrar {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultBuildOperationNotificationListenerRegistrar.class);

    private final Action<? super BuildOperationListener> listenerSubscriber;

    public DefaultBuildOperationNotificationListenerRegistrar(Action<? super BuildOperationListener> listenerSubscriber) {
        this.listenerSubscriber = listenerSubscriber;
    }

    @Override
    public void registerBuildScopeListener(BuildOperationNotificationListener listener) {
        listenerSubscriber.execute(new Listener(listener));
    }

    private static class Listener implements BuildOperationListener {
        private final BuildOperationNotificationListener listener;

        private Listener(BuildOperationNotificationListener listener) {
            this.listener = listener;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            // replace this with opt-in to exposing on producer side
            // it just so happens right now that this is a reasonable heuristic
            if (buildOperation.getDetails() == null) {
                return;
            }

            Started notification = new Started(buildOperation.getId(), buildOperation.getParentId(), buildOperation.getDetails());
            try {
                listener.started(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            // replace this with opt-in to exposing on producer side
            // it just so happens right now that this is a reasonable heuristic
            if (finishEvent.getResult() == null) {
                return;
            }

            Finished notification = new Finished(buildOperation.getId(), finishEvent.getResult());
            try {
                listener.finished(notification);
            } catch (Exception e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
            }
        }
    }

    private static class Started implements BuildOperationStartedNotification {

        private final Object id;
        private final Object parentId;
        private final Object details;

        private Started(Object id, Object parentId, Object details) {
            this.id = id;
            this.parentId = parentId;
            this.details = details;
        }

        @Override
        public Object getId() {
            return id;
        }

        @Override
        public Object getParentId() {
            return parentId;
        }

        @Override
        public Object getDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{id=" + id + ", parentId=" + parentId + ", details=" + details + '}';
        }
    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final Object id;
        private final Object result;

        private Finished(Object id, Object result) {
            this.id = id;
            this.result = result;
        }

        @Override
        public Object getId() {
            return id;
        }

        @Override
        public Object getResult() {
            return result;
        }

        @Override
        public String toString() {
            return "BuildOperationFinishedNotification{id=" + id + ", result=" + result + '}';
        }
    }

}
