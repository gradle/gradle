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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.internal.GradleInternal;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationListener;
import org.gradle.internal.progress.BuildOperationListenerManager;
import org.gradle.internal.progress.OperationFinishEvent;
import org.gradle.internal.progress.OperationProgressEvent;
import org.gradle.internal.progress.OperationStartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BuildOperationNotificationBridge implements BuildOperationNotificationListenerRegistrar, Stoppable {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildOperationNotificationBridge.class);

    private final BuildOperationListenerManager listenerManager;

    private final ReplayAndAttachListener replayAndAttachListener = new ReplayAndAttachListener();
    private BuildOperationListener adapter = new Adapter(replayAndAttachListener);

    private BuildOperationNotificationListener2 listener;
    private boolean stopped;

    BuildOperationNotificationBridge(BuildOperationListenerManager buildOperationListenerManager) {
        this.listenerManager = buildOperationListenerManager;
    }

    public void start(GradleInternal gradle) {
        listenerManager.addListener(adapter);

        // ensure we only store events for configuration phase to keep overhead small
        // when build scan plugin is not applied
        gradle.rootProject(new Action<Project>() {
            @Override
            public void execute(@SuppressWarnings("NullableProblems") Project project) {
                project.afterEvaluate(new Action<Project>() {
                    @Override
                    public void execute(@SuppressWarnings("NullableProblems") Project project) {
                        if (listener == null) {
                            stop();
                        }
                    }
                });
            }
        });
    }

    @Override
    public void registerBuildScopeListener(BuildOperationNotificationListener notificationListener) {
        BuildOperationNotificationListener2 adapted = adapt(notificationListener);
        assignSingleListener(adapted);

        // Remove the old adapter and start again.
        // We explicitly do not want to receive finish notifications
        // for any operations currently in flight,
        // and we want to throw away the recorded notifications.

        listenerManager.removeListener(adapter);
        adapter = new Adapter(adapted);
        listenerManager.addListener(adapter);
    }

    @Override
    public void registerBuildScopeListenerAndReceiveStoredOperations(BuildOperationNotificationListener notificationListener) {
        register(adapt(notificationListener));
    }

    @Override
    public void register(BuildOperationNotificationListener2 listener) {
        assignSingleListener(listener);
        replayAndAttachListener.attach(listener);
    }

    private void assignSingleListener(BuildOperationNotificationListener2 notificationListener) {
        if (listener != null) {
            throw new IllegalStateException("listener is already registered (implementation class " + listener.getClass().getName() + ")");
        }
        listener = notificationListener;
    }

    @Override
    public void stop() {
        if (!stopped) {
            listenerManager.removeListener(adapter);
            adapter = null;
            stopped = true;
        }
    }

    /*
        Note: the intention here is to work towards not having to create new objects
        to meet the notification object interfaces.
        Instead, the base types like BuildOperationDescriptor should implement them natively.
        However, this will require restructuring this type and associated things such as
        OperationStartEvent. This will happen later.
     */
    // No synchronization as Gradle event dispatching guarantees serialization and memory fences.
    private static class Adapter implements BuildOperationListener {

        private final BuildOperationNotificationListener2 notificationListener;

        private final Map<Object, Object> parents = new ConcurrentHashMap<Object, Object>();
        private final Map<Object, Object> active = new ConcurrentHashMap<Object, Object>();

        private Adapter(BuildOperationNotificationListener2 notificationListener) {
            this.notificationListener = notificationListener;
        }

        @Override
        public void started(BuildOperationDescriptor buildOperation, OperationStartEvent startEvent) {
            Object id = buildOperation.getId();
            Object parentId = buildOperation.getParentId();
            if (parentId != null) {
                if (active.containsKey(parentId)) {
                    parents.put(id, parentId);
                } else {
                    parentId = parents.get(parentId);
                    if (parentId != null) {
                        parents.put(id, parentId);
                    }
                }
            }

            if (buildOperation.getDetails() == null) {
                return;
            }

            active.put(id, "");

            Started notification = new Started(startEvent.getStartTime(), id, parentId, buildOperation.getDetails());

            try {
                notificationListener.started(notification);
            } catch (Throwable e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
                maybeThrow(e);
            }
        }

        private void maybeThrow(Throwable e) {
            if (e instanceof Error && !(e instanceof LinkageError)) {
                throw (Error) e;
            }
        }

        @Override
        public void progress(BuildOperationDescriptor buildOperation, OperationProgressEvent progressEvent) {
            Object id = buildOperation.getId();
            if (!active.containsKey(id)) {
                return;
            }

            Object details = progressEvent.getDetails();
            if (details == null) {
                return;
            }

            notificationListener.progress(new Progress(buildOperation.getId(), progressEvent.getTime(), details));
        }

        @Override
        public void finished(BuildOperationDescriptor buildOperation, OperationFinishEvent finishEvent) {
            Object id = buildOperation.getId();
            Object parentId = parents.remove(id);
            if (active.remove(id) == null) {
                return;
            }

            Finished notification = new Finished(finishEvent.getEndTime(), id, parentId, buildOperation.getDetails(), finishEvent.getResult(), finishEvent.getFailure());
            try {
                notificationListener.finished(notification);
            } catch (Throwable e) {
                LOGGER.debug("Build operation notification listener threw an error on " + notification, e);
                maybeThrow(e);
            }
        }


    }

    // No synchronization as Gradle event dispatching guarantees serialization and memory fences.
    private static class RecordingListener implements BuildOperationNotificationListener2 {

        private final List<Object> storedEvents = Lists.newArrayList();

        @Override
        public void started(BuildOperationStartedNotification notification) {
            storedEvents.add(notification);
        }

        @Override
        public void progress(BuildOperationProgressNotification notification) {
            storedEvents.add(notification);
        }

        @Override
        public void finished(BuildOperationFinishedNotification notification) {
            storedEvents.add(notification);
        }


    }

    // Synchronization required as attach could happen concurrently with receive
    private static class ReplayAndAttachListener implements BuildOperationNotificationListener2 {

        private RecordingListener recordingListener = new RecordingListener();

        private BuildOperationNotificationListener2 listener = recordingListener;

        private synchronized void attach(BuildOperationNotificationListener2 realListener) {
            for (Object storedEvent : recordingListener.storedEvents) {
                if (storedEvent instanceof BuildOperationStartedNotification) {
                    realListener.started((BuildOperationStartedNotification) storedEvent);
                } else if (storedEvent instanceof BuildOperationProgressNotification) {
                    realListener.progress((BuildOperationProgressNotification) storedEvent);
                } else if (storedEvent instanceof BuildOperationFinishedNotification) {
                    realListener.finished((BuildOperationFinishedNotification) storedEvent);
                }

            }

            this.listener = realListener;
            this.recordingListener = null; // release
        }

        @Override
        public synchronized void started(BuildOperationStartedNotification notification) {
            listener.started(notification);
        }

        @Override
        public void progress(BuildOperationProgressNotification notification) {
            listener.progress(notification);
        }

        @Override
        public synchronized void finished(BuildOperationFinishedNotification notification) {
            listener.finished(notification);
        }


    }

    private static class Started implements BuildOperationStartedNotification {

        private final long timestamp;

        private final Object id;
        private final Object parentId;
        private final Object details;

        private Started(long timestamp, Object id, Object parentId, Object details) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
        }

        @Override
        public long getNotificationOperationStartedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
        }

        @Override
        public Object getNotificationOperationDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "BuildOperationStartedNotification{"
                + "id=" + id
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + '}';
        }


    }

    private static class Progress implements BuildOperationProgressNotification {

        private final Object id;

        private final long timestamp;
        private final Object details;

        public Progress(Object id, long timestamp, Object details) {
            this.id = id;
            this.timestamp = timestamp;
            this.details = details;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Override
        public long getNotificationOperationProgressTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationProgressDetails() {
            return details;
        }

    }

    private static class Finished implements BuildOperationFinishedNotification {

        private final long timestamp;

        private final Object id;
        private final Object parentId;
        private final Object details;
        private final Object result;
        private final Throwable failure;

        private Finished(long timestamp, Object id, Object parentId, Object details, Object result, Throwable failure) {
            this.timestamp = timestamp;
            this.id = id;
            this.parentId = parentId;
            this.details = details;
            this.result = result;
            this.failure = failure;
        }

        @Override
        public long getNotificationOperationFinishedTimestamp() {
            return timestamp;
        }

        @Override
        public Object getNotificationOperationId() {
            return id;
        }

        @Nullable
        @Override
        public Object getNotificationOperationParentId() {
            return parentId;
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
                + ", parentId=" + parentId
                + ", timestamp=" + timestamp
                + ", details=" + details
                + ", result=" + result
                + ", failure=" + failure
                + '}';
        }
    }

    private static BuildOperationNotificationListener2 adapt(final BuildOperationNotificationListener listener) {
        return new BuildOperationNotificationListener2() {
            @Override
            public void started(BuildOperationStartedNotification notification) {
                listener.started(notification);
            }

            @Override
            public void progress(BuildOperationProgressNotification notification) {

            }

            @Override
            public void finished(BuildOperationFinishedNotification notification) {
                listener.finished(notification);
            }
        };
    }
}
